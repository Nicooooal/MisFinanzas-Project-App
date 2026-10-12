package com.usuario.finanzas.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.security.AlmacenSeguro
import com.usuario.finanzas.data.preferences.CoordinadorDatos
import com.usuario.finanzas.domain.finanzas.Dinero
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Respaldo portable sin incluir PIN, claves del dispositivo ni archivos fuera de comprobantes. */
@Singleton
class RespaldoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FinanzasDatabase,
    private val almacenSeguro: AlmacenSeguro,
    private val coordinador: CoordinadorDatos
) {
    private val prefs get() = context.getSharedPreferences("finanzas_preferences", Context.MODE_PRIVATE)
    private val directorio get() = File(context.filesDir, "comprobantes").canonicalFile

    suspend fun exportar(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        // Preparar y cifrar todo antes de abrir el destino, para no truncarlo ante datos inválidos.
        val bytes = coordinador.mantener {
            val datos = db.withTransaction { capturarDatos() }
            CodecRespaldo.cifrar(ArchivoRespaldo.escribir(datos), password)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
            ?: error("No se pudo abrir el destino del respaldo.")
    }

    suspend fun restaurar(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val cifrado = context.contentResolver.openInputStream(uri)?.use { leerLimitado(it, CodecRespaldo.MAX_BYTES) }
            ?: error("No se pudo abrir el respaldo.")
        val claro = CodecRespaldo.descifrar(cifrado, password)
        val datos = try { ArchivoRespaldo.leer(claro) } finally { claro.fill(0) }
        coordinador.mantener {
            validar(datos)
            val nuevos = linkedMapOf<String, File>()
            var confirmado = false
            val preferenciasAnteriores = leerPreferencias()
            try {
                require(directorio.isDirectory || directorio.mkdirs()) { "No se pudo preparar el directorio de comprobantes." }
                datos.archivos.forEach { (nombre, contenido) ->
                    val extension = nombre.substringAfterLast('.', "bin")
                    val archivo = File(directorio, "${UUID.randomUUID()}.$extension")
                    nuevos[nombre] = archivo
                    archivo.outputStream().use { it.write(contenido); it.fd.sync() }
                }
                // Desde aquí la cancelación no puede eliminar comprobantes de una BD ya confirmada.
                withContext(NonCancellable) {
                    try {
                        db.withTransaction {
                            val sqlite = db.openHelper.writableDatabase
                            vaciar(sqlite)
                            insertar(datos.tablas, { nombre -> nuevos.getValue(nombre).absolutePath }, sqlite::execSQL)
                            comprobarRelaciones(sqlite)
                            aplicarPreferencias(datos)
                        }
                        confirmado = true
                    } catch (error: Throwable) {
                        aplicarPreferencias(preferenciasAnteriores)
                        throw error
                    }
                    limpiarComprobantes(nuevos.values.toSet())
                }
            } finally {
                if (!confirmado) nuevos.values.forEach { it.delete() }
            }
        }
    }

    suspend fun borrarDatos(pin: String) = withContext(Dispatchers.IO) {
        coordinador.mantener {
            require(almacenSeguro.validarPin(pin)) {
                val segundos = almacenSeguro.segundosBloqueoPin()
                if (segundos > 0) "Demasiados intentos. Espera $segundos segundos." else "El PIN es incorrecto."
            }
            withContext(NonCancellable) {
                db.withTransaction {
                    val sqlite = db.openHelper.writableDatabase
                    vaciar(sqlite)
                    // Reutilizar la misma precarga conserva la configuración de una instalación nueva.
                    FinanzasDatabase.CALLBACK_PRECARGA.onCreate(sqlite)
                }
                limpiarComprobantes(emptySet())
            }
            // Se conservan PIN y passphrase: la conexión SQLCipher sigue usando la misma llave.
        }
    }

    private fun capturarDatos(): DatosRespaldo {
        val sqlite = db.openHelper.readableDatabase
        require(sqlite.version == ArchivoRespaldo.VERSION_BD) { "La versión de la base de datos no es compatible." }
        val archivos = linkedMapOf<String, ByteArray>()
        var filasTotales = 0
        var bytesAdjuntos = 0L
        val tablas = TABLAS.map { nombre ->
            val columnas = columnas(sqlite, nombre)
            val filas = mutableListOf<List<Any?>>()
            sqlite.query("SELECT * FROM `$nombre`").use { cursor ->
                while (cursor.moveToNext()) {
                    require(++filasTotales <= ArchivoRespaldo.MAX_FILAS) { "Hay demasiados registros para un solo respaldo." }
                    filas += columnas.map { columna ->
                        val indice = cursor.getColumnIndexOrThrow(columna.nombre)
                        val valor = when (cursor.getType(indice)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(indice)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(indice)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(indice)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(indice)
                            else -> error("Tipo de dato desconocido.")
                        }
                        if (columna.nombre == "comprobantePath" && valor is String) {
                            val archivo = comprobanteLocal(valor)
                            require(archivo.isFile) { "Falta un comprobante. Revisa sus adjuntos antes de exportar." }
                            if (!archivos.containsKey(archivo.name)) {
                                require(archivos.size < ArchivoRespaldo.MAX_ARCHIVOS) { "Hay demasiados comprobantes." }
                                val contenido = archivo.inputStream().use { leerLimitado(it, ArchivoRespaldo.MAX_ADJUNTO) }
                                bytesAdjuntos += contenido.size
                                require(bytesAdjuntos <= CodecRespaldo.MAX_BYTES) { "Los comprobantes superan 64 MB." }
                                archivos[archivo.name] = contenido
                            }
                            archivo.name
                        } else valor
                    }
                }
            }
            TablaRespaldo(nombre, columnas, filas)
        }
        return leerPreferencias().copy(tablas = tablas, archivos = archivos)
    }

    private fun validar(datos: DatosRespaldo) {
        val sqlite = db.openHelper.readableDatabase
        require(sqlite.version == ArchivoRespaldo.VERSION_BD) { "La versión de la base de datos no es compatible." }
        require(datos.tablas.size == TABLAS.size && datos.tablas.map { it.nombre }.toSet() == TABLAS.toSet()) {
            "El respaldo contiene tablas desconocidas o incompletas."
        }
        require(datos.tablas.single { it.nombre == "configuracion_financiera" }.filas.size == 1) {
            "El respaldo debe conservar su moneda y saldo inicial."
        }
        val referenciados = mutableSetOf<String>()
        datos.tablas.forEach { tabla ->
            val esquema = columnas(sqlite, tabla.nombre)
            require(tabla.columnas.size == esquema.size && tabla.columnas.toSet() == esquema.toSet()) {
                "La estructura del respaldo no es compatible."
            }
            tabla.filas.forEach { fila ->
                tabla.columnas.forEachIndexed { i, c ->
                    val valor = fila[i]
                    require(valor != null || !c.obligatoria) { "El respaldo tiene campos obligatorios vacíos." }
                    if (valor != null) require(when (c.tipo.uppercase()) {
                        "INTEGER" -> valor is Long
                        "REAL" -> valor is Double && valor.isFinite()
                        "TEXT" -> valor is String
                        "BLOB" -> valor is ByteArray
                        else -> false
                    }) { "El respaldo contiene un dato incompatible." }
                    if (c.nombre == "comprobantePath" && valor != null) {
                        require(valor is String && ArchivoRespaldo.nombreSeguro(valor) && datos.archivos.containsKey(valor)) {
                            "El respaldo contiene un comprobante ausente o una ruta no válida."
                        }
                        referenciados += valor
                    }
                }
                validarValores(tabla, fila)
            }
        }
        require(referenciados == datos.archivos.keys) { "El respaldo contiene adjuntos sin referencia." }
        // SQLite en memoria valida PK, restricciones y FK sin tocar la base cifrada del usuario.
        SQLiteDatabase.create(null).use { temporal ->
            temporal.setForeignKeyConstraintsEnabled(true)
            TABLAS.forEach { nombre ->
                sqlite.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(nombre)).use { cursor ->
                    check(cursor.moveToFirst())
                    temporal.execSQL(cursor.getString(0))
                }
            }
            insertar(datos.tablas, { it }, temporal::execSQL)
            temporal.rawQuery("PRAGMA foreign_key_check", null).use {
                require(!it.moveToFirst()) { "El respaldo contiene referencias inválidas." }
            }
        }
    }

    private fun validarValores(tabla: TablaRespaldo, fila: List<Any?>) {
        val campos = tabla.columnas.mapIndexed { indice, c -> c.nombre to fila[indice] }.toMap()
        fun valor(nombre: String) = campos[nombre]
        fun enumerado(nombre: String, opciones: Set<String>) {
            if (campos.containsKey(nombre)) require(valor(nombre) in opciones) { "El respaldo contiene un $nombre inválido." }
        }
        listOf("activo", "archivada", "onboardingCompleto", "emparejada").forEach { campo ->
            if (campos.containsKey(campo)) require(valor(campo) in listOf(0L, 1L)) { "El respaldo contiene una opción inválida." }
        }
        if (tabla.nombre == "categorias") enumerado("direccion", setOf("entrada", "salida", "ambas"))
        else enumerado("direccion", setOf("entrada", "salida"))
        enumerado("ciclo", setOf("semanal", "mensual", "anual"))
        enumerado("frecuencia", setOf("diario", "semanal", "quincenal", "mensual", "anual"))
        if (tabla.nombre == "servicios") {
            require((valor("diaCorte") as? Long) in (1L..31L) && (valor("diaLimite") as? Long) in (1L..31L)) { "El respaldo contiene fechas de servicio inválidas." }
            enumerado("estado", setOf("pendiente", "pagado", "vencido"))
        }
        listOf("monto", "montoEstimado").forEach { campo ->
            (valor(campo) as? Double)?.let {
                require(it >= 0 && Dinero.aCentavos(it) in 0..Dinero.MAX_CENTAVOS) {
                    "El respaldo contiene un importe fuera del rango permitido."
                }
            }
        }
        listOf("montoCentavos", "limiteCentavos", "objetivoCentavos", "saldoInicialCentavos").forEach { campo ->
            val minimo = if (tabla.nombre == "aportes_meta" || campo == "saldoInicialCentavos") -Dinero.MAX_CENTAVOS else 0L
            (valor(campo) as? Long)?.let {
                require(it in minimo..Dinero.MAX_CENTAVOS) { "El respaldo contiene un importe fuera del rango permitido." }
            }
        }
        if (tabla.nombre == "transacciones") {
            (valor("montoCentavos") as? Long)?.let { centavos ->
                require(centavos == Dinero.aCentavos(valor("monto") as Double)) {
                    "El respaldo contiene dos importes distintos para un mismo movimiento."
                }
            }
        }
        if (tabla.nombre == "configuracion_financiera") {
            require(valor("id") == 1L) { "El respaldo contiene una configuración inválida." }
            enumerado("moneda", setOf("COP", "USD", "EUR", "BRL"))
        }
    }

    private fun insertar(tablas: List<TablaRespaldo>, ruta: (String) -> String, ejecutar: (String, Array<Any?>) -> Unit) {
        TABLAS.forEach { nombre ->
            val tabla = tablas.single { it.nombre == nombre }
            val columnas = tabla.columnas.joinToString(",") { "`${it.nombre}`" }
            val marcadores = tabla.columnas.joinToString(",") { "?" }
            val indiceAdjunto = tabla.columnas.indexOfFirst { it.nombre == "comprobantePath" }
            tabla.filas.forEach { fila ->
                val valores = fila.toTypedArray()
                if (indiceAdjunto >= 0) (valores[indiceAdjunto] as? String)?.let { valores[indiceAdjunto] = ruta(it) }
                ejecutar("INSERT INTO `$nombre` ($columnas) VALUES ($marcadores)", valores)
            }
        }
    }

    private fun columnas(sqlite: SupportSQLiteDatabase, tabla: String): List<ColumnaRespaldo> =
        sqlite.query("PRAGMA table_info(`$tabla`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ColumnaRespaldo(
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 0,
                    cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                ))
            }
        }

    private fun vaciar(sqlite: SupportSQLiteDatabase) {
        TABLAS.asReversed().forEach { sqlite.execSQL("DELETE FROM `$it`") }
    }

    private fun comprobarRelaciones(sqlite: SupportSQLiteDatabase) {
        sqlite.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "El respaldo tiene referencias inválidas." } }
    }

    private fun comprobanteLocal(ruta: String): File {
        val original = File(ruta)
        val archivo = if (original.isAbsolute) original.canonicalFile else File(context.filesDir, ruta).canonicalFile
        require(archivo.parentFile == directorio && ArchivoRespaldo.nombreSeguro(archivo.name)) {
            "Un comprobante está fuera del almacenamiento privado permitido."
        }
        return archivo
    }

    private fun limpiarComprobantes(conservar: Set<File>) {
        directorio.listFiles()?.forEach { archivo ->
            if (archivo.canonicalFile.parentFile == directorio && archivo.isFile && archivo !in conservar) archivo.delete()
        }
    }

    private fun leerPreferencias() = DatosRespaldo(emptyList(), emptyMap(),
        prefs.getString("idioma", "es") ?: "es", prefs.getString("tema", "sistema") ?: "sistema",
        prefs.getBoolean("ocultar", false), prefs.getBoolean("biometria", true))

    private fun aplicarPreferencias(datos: DatosRespaldo) {
        check(prefs.edit().putString("idioma", datos.idioma).putString("tema", datos.tema)
            .putBoolean("ocultar", datos.ocultar).putBoolean("biometria", datos.biometria).commit()) {
            "No se pudieron guardar las preferencias restauradas."
        }
    }

    private fun leerLimitado(entrada: InputStream, maximo: Int): ByteArray {
        val memoria = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val cantidad = entrada.read(buffer)
            if (cantidad < 0) break
            require(memoria.size().toLong() + cantidad <= maximo) { "El archivo supera el tamaño permitido." }
            memoria.write(buffer, 0, cantidad)
        }
        return memoria.toByteArray()
    }

    private companion object {
        // Padres antes que hijos: también permite borrar en orden inverso con FK activas.
        val TABLAS = listOf("categorias", "servicios", "metas", "configuracion_financiera",
            "transacciones", "presupuestos", "recurrentes", "historial_pagos", "aprendizaje_categorias",
            "transacciones_pendientes", "registro_detecciones", "aportes_meta", "ocurrencias_recurrentes")
    }
}
