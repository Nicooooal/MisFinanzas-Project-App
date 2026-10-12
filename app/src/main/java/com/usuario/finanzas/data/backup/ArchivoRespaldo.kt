package com.usuario.finanzas.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal data class ColumnaRespaldo(val nombre: String, val tipo: String, val obligatoria: Boolean, val primaria: Int)
internal data class TablaRespaldo(val nombre: String, val columnas: List<ColumnaRespaldo>, val filas: List<List<Any?>>)
internal data class DatosRespaldo(
    val tablas: List<TablaRespaldo>, val archivos: Map<String, ByteArray>,
    val idioma: String, val tema: String, val ocultar: Boolean, val biometria: Boolean
)

/** Contenedor sin compresión ni extracción ZIP: límites antes de cada asignación. */
internal object ArchivoRespaldo {
    const val VERSION_BD = 5
    const val MAX_FILAS = 200_000
    const val MAX_ARCHIVOS = 2_000
    const val MAX_ADJUNTO = 16 * 1024 * 1024
    private const val MAX_TEXTO = 1024 * 1024
    private const val MAGIC = 0x4d464431

    fun escribir(datos: DatosRespaldo): ByteArray {
        val memoria = object : ByteArrayOutputStream() {
            override fun write(b: Int) { limite(count.toLong() + 1); super.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { limite(count.toLong() + len); super.write(b, off, len) }
            fun limite(n: Long) { require(n <= CodecRespaldo.MAX_BYTES - 68) { "El respaldo supera el límite de 64 MB." } }
        }
        DataOutputStream(memoria).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION_BD)
            out.texto(datos.idioma); out.texto(datos.tema)
            out.writeBoolean(datos.ocultar); out.writeBoolean(datos.biometria)
            out.writeInt(datos.tablas.size)
            datos.tablas.forEach { tabla ->
                out.texto(tabla.nombre); out.writeInt(tabla.columnas.size)
                tabla.columnas.forEach { c -> out.texto(c.nombre); out.texto(c.tipo); out.writeBoolean(c.obligatoria); out.writeInt(c.primaria) }
                out.writeInt(tabla.filas.size)
                tabla.filas.forEach { fila -> fila.forEach { valor ->
                    when (valor) {
                        null -> out.writeByte(0)
                        is Long -> { out.writeByte(1); out.writeLong(valor) }
                        is Double -> { require(valor.isFinite()); out.writeByte(2); out.writeDouble(valor) }
                        is String -> { out.writeByte(3); out.texto(valor) }
                        is ByteArray -> { require(valor.size <= MAX_TEXTO); out.writeByte(4); out.writeInt(valor.size); out.write(valor) }
                        else -> error("Tipo de dato no compatible con el respaldo.")
                    }
                } }
            }
            require(datos.archivos.size <= MAX_ARCHIVOS) { "Hay demasiados comprobantes para un solo respaldo." }
            out.writeInt(datos.archivos.size)
            datos.archivos.forEach { (nombre, contenido) ->
                require(nombreSeguro(nombre)); require(contenido.size <= MAX_ADJUNTO)
                out.texto(nombre); out.writeInt(contenido.size); out.write(contenido)
            }
        }
        return memoria.toByteArray()
    }

    fun leer(bytes: ByteArray): DatosRespaldo = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "El contenido del respaldo no es válido." }
        require(input.readInt() == VERSION_BD) { "El respaldo pertenece a una versión de datos no compatible." }
        val idioma = input.texto(); val tema = input.texto()
        require(idioma in listOf("es", "en", "pt", "fr") && tema in listOf("sistema", "claro", "oscuro")) { "Las preferencias del respaldo no son válidas." }
        val ocultar = input.readBoolean(); val biometria = input.readBoolean()
        var filasTotales = 0
        val tablas = List(input.cantidad(1, 50)) {
            val nombre = input.texto()
            val columnas = List(input.cantidad(1, 128)) {
                ColumnaRespaldo(input.texto(), input.texto(), input.readBoolean(), input.cantidad(0, 128))
            }
            val cantidad = input.cantidad(0, MAX_FILAS)
            require(cantidad.toLong() * columnas.size <= input.available()) { "El respaldo está incompleto." }
            filasTotales += cantidad
            require(filasTotales <= MAX_FILAS) { "El respaldo contiene demasiados registros." }
            val filas = List(cantidad) {
                List(columnas.size) {
                    when (input.readUnsignedByte()) {
                        0 -> null
                        1 -> input.readLong()
                        2 -> input.readDouble().also { require(it.isFinite()) }
                        3 -> input.texto()
                        4 -> input.bytes(MAX_TEXTO)
                        else -> error("El respaldo contiene un tipo de dato desconocido.")
                    }
                }
            }
            TablaRespaldo(nombre, columnas, filas)
        }
        val archivos = linkedMapOf<String, ByteArray>()
        repeat(input.cantidad(0, MAX_ARCHIVOS)) {
            val nombre = input.texto()
            require(nombreSeguro(nombre) && !archivos.containsKey(nombre)) { "El respaldo contiene una ruta de comprobante no válida." }
            archivos[nombre] = input.bytes(MAX_ADJUNTO)
        }
        require(input.available() == 0) { "El respaldo contiene datos inesperados." }
        DatosRespaldo(tablas, archivos, idioma, tema, ocultar, biometria)
    }

    fun nombreSeguro(nombre: String): Boolean = nombre.length in 1..100 &&
        nombre.matches(Regex("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9]{1,12})?"))

    private fun DataOutputStream.texto(texto: String) {
        val datos = texto.toByteArray(Charsets.UTF_8)
        require(datos.size <= MAX_TEXTO) { "Un campo de texto supera el límite del respaldo." }
        writeInt(datos.size); write(datos)
    }
    private fun DataInputStream.texto(): String = bytes(MAX_TEXTO).toString(Charsets.UTF_8)
    private fun DataInputStream.bytes(maximo: Int): ByteArray {
        val cantidad = cantidad(0, maximo)
        require(cantidad <= available()) { "El respaldo está incompleto." }
        return ByteArray(cantidad).also(::readFully)
    }
    private fun DataInputStream.cantidad(minimo: Int, maximo: Int): Int = readInt().also {
        require(it in minimo..maximo) { "El respaldo contiene tamaños o cantidades no válidos." }
    }
}
