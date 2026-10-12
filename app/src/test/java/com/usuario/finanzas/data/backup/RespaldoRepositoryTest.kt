package com.usuario.finanzas.data.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.data.local.security.AlmacenSeguro
import com.usuario.finanzas.data.preferences.CoordinadorDatos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RespaldoRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: FinanzasDatabase
    private lateinit var seguro: AlmacenSeguro
    private lateinit var repo: RespaldoRepository
    private lateinit var respaldo: File
    private lateinit var recibo: File
    private val password = "Respaldo de prueba 2026"
    private val prefs get() = context.getSharedPreferences("finanzas_preferences", Context.MODE_PRIVATE)

    @Before fun preparar() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, FinanzasDatabase::class.java).allowMainThreadQueries().build()
        seguro = AlmacenSeguro(context.getSharedPreferences("seguridad-${UUID.randomUUID()}", Context.MODE_PRIVATE))
        seguro.guardarPin("1234")
        repo = RespaldoRepository(context, db, seguro, CoordinadorDatos())
        respaldo = File(context.cacheDir, "${UUID.randomUUID()}.mfrb")
        val directorio = File(context.filesDir, "comprobantes").apply { mkdirs() }
        recibo = File(directorio, "${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        db.categoriaDao().insertar(Categoria(1, "Otros", "#999999", "Category"))
        db.servicioDao().insertar(Servicio(2, "Internet", 25000.5, 1, 15))
        db.transaccionDao().insertar(Transaccion(7, 25000.5, 1, "Café y niño", "Comercio", 1000,
            "manual", recibo.absolutePath, 2, "salida", "pago_servicio", "Nequi", 2500050, "hogar"))
        db.historialPagoDao().insertar(HistorialPago(3, 2, 25000.5, 1000, recibo.absolutePath))
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("INSERT INTO aprendizaje_categorias VALUES ('comercio', 1, 2, 1000)")
        sqlite.execSQL("INSERT INTO transacciones_pendientes VALUES (1, 15000, 'Pendiente', 1000, 'ingreso', 'Nequi', 'Texto original', 'entrada')")
        sqlite.execSQL("INSERT INTO registro_detecciones VALUES ('clave', 'coincidencia', 'SMS', 1000, 0)")
        db.finanzasDao().guardarConfiguracion(ConfiguracionFinanciera(moneda = "USD", saldoInicialCentavos = 10000000, onboardingCompleto = true))
        db.finanzasDao().insertarPresupuesto(Presupuesto(1, "Mensual", 5000000, 1))
        db.finanzasDao().insertarMeta(Meta(1, "Viaje", 90000000))
        db.finanzasDao().insertarAporte(AporteMeta(1, 1, 50000, 1000, "Primer aporte"))
        db.finanzasDao().insertarRecurrente(Recurrente(1, "Internet", 2500050, 1, "salida", "mensual", 1000))
        db.finanzasDao().insertarOcurrencia(OcurrenciaRecurrente(1, 1000, 7, 1001))
        prefs.edit().putString("idioma", "pt").putString("tema", "oscuro").putBoolean("ocultar", true)
            .putBoolean("biometria", false).commit()
        Unit
    }

    @After fun cerrar() { db.close(); respaldo.delete() }

    @Test fun restauraTodasLasTablasPreferenciasYAdjuntosSinTransportarClaves() = runBlocking {
        val claveLocal = seguro.obtenerPassphraseBd()
        repo.exportar(Uri.fromFile(respaldo), password)
        val contenido = ArchivoRespaldo.leer(CodecRespaldo.descifrar(respaldo.readBytes(), password))
        assertEquals(13, contenido.tablas.size)
        assertTrue(contenido.tablas.all { it.filas.isNotEmpty() })
        assertEquals(setOf(recibo.name), contenido.archivos.keys)
        assertFalse(String(ArchivoRespaldo.escribir(contenido)).contains(claveLocal))
        repo.borrarDatos("1234")
        assertFalse(recibo.exists())
        prefs.edit().putString("idioma", "es").putBoolean("biometria", true).commit()
        repo.restaurar(Uri.fromFile(respaldo), password)
        val restaurada = db.finanzasDao().transaccion(7)!!
        assertEquals("Café y niño", restaurada.descripcion)
        assertEquals(2500050L, restaurada.montoCentavos)
        assertEquals(10000000L, db.finanzasDao().configuracion()!!.saldoInicialCentavos)
        assertEquals("Primer aporte", db.finanzasDao().aportes(1).single().nota)
        assertEquals(7, db.finanzasDao().ocurrencia(1, 1000)!!.transaccionId)
        val adjunto = File(restaurada.comprobantePath!!)
        assertNotEquals(recibo.absolutePath, adjunto.absolutePath)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), adjunto.readBytes())
        assertEquals(adjunto.absolutePath, db.historialPagoDao().observarPorServicio(2).first().single().comprobantePath)
        assertEquals("pt", prefs.getString("idioma", null))
        assertEquals("oscuro", prefs.getString("tema", null))
        assertTrue(prefs.getBoolean("ocultar", false))
        assertFalse(prefs.getBoolean("biometria", true))
        assertTrue(seguro.validarPin("1234"))
        assertEquals(claveLocal, seguro.obtenerPassphraseBd())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test fun contrasenaErroneaYCorrupcionNoCambianDatosArchivosNiPreferencias() = runBlocking {
        repo.exportar(Uri.fromFile(respaldo), password)
        val original = respaldo.readBytes()
        suspend fun comprobarSinCambios(clave: String) {
            assertTrue(runCatching { repo.restaurar(Uri.fromFile(respaldo), clave) }.isFailure)
            assertEquals("Café y niño", db.finanzasDao().transaccion(7)!!.descripcion)
            assertEquals("pt", prefs.getString("idioma", null))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), recibo.readBytes())
        }
        comprobarSinCambios("contraseña incorrecta")
        respaldo.writeBytes(original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        comprobarSinCambios(password)
    }

    @Test fun referenciasInvalidasYRutasAjenasSeRechazanAntesDeReemplazar() = runBlocking {
        repo.exportar(Uri.fromFile(respaldo), password)
        val original = ArchivoRespaldo.leer(CodecRespaldo.descifrar(respaldo.readBytes(), password))
        listOf("categoriaId" to 99999L, "comprobantePath" to "../secreto.xml").forEach { (campo, valor) ->
            val modificado = original.copy(tablas = original.tablas.map { tabla ->
                if (tabla.nombre != "transacciones") tabla else {
                    val indice = tabla.columnas.indexOfFirst { it.nombre == campo }
                    tabla.copy(filas = tabla.filas.map { it.toMutableList().apply { this[indice] = valor } })
                }
            })
            respaldo.writeBytes(CodecRespaldo.cifrar(ArchivoRespaldo.escribir(modificado), password))
            assertTrue(runCatching { repo.restaurar(Uri.fromFile(respaldo), password) }.isFailure)
            assertEquals(1, db.finanzasDao().transaccion(7)!!.categoriaId)
            assertEquals(recibo.absolutePath, db.finanzasDao().transaccion(7)!!.comprobantePath)
            assertEquals("pt", prefs.getString("idioma", null))
            assertTrue(recibo.isFile)
        }
    }

    @Test fun falloDuranteReemplazoRevierteTransaccionYEliminaSoloAdjuntosNuevos() = runBlocking {
        repo.exportar(Uri.fromFile(respaldo), password)
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("UPDATE transacciones SET descripcion = 'Dato posterior al respaldo' WHERE id = 7")
        sqlite.execSQL("CREATE TRIGGER impedir_restore BEFORE INSERT ON transacciones WHEN NEW.id = 7 BEGIN SELECT RAISE(ABORT, 'Error simulado'); END")
        prefs.edit().putString("idioma", "fr").commit()
        val archivosPrevios = recibo.parentFile!!.list()!!.toSet()
        assertTrue(runCatching { repo.restaurar(Uri.fromFile(respaldo), password) }.isFailure)
        assertEquals("Dato posterior al respaldo", db.finanzasDao().transaccion(7)!!.descripcion)
        assertEquals("fr", prefs.getString("idioma", null))
        assertEquals(archivosPrevios, recibo.parentFile!!.list()!!.toSet())
        assertTrue(recibo.isFile)
    }

    @Test fun borradoRevalidaPinConservaLlaveYReconstruyeCategoriasBase() = runBlocking {
        val clave = seguro.obtenerPassphraseBd()
        assertTrue(runCatching { repo.borrarDatos("0000") }.isFailure)
        assertNotNull(db.finanzasDao().transaccion(7))
        repo.borrarDatos("1234")
        assertEquals(0, db.finanzasDao().contarDatosFinancieros())
        assertTrue(db.categoriaDao().obtenerTodas().any { it.nombre == "Otros" })
        assertTrue(db.categoriaDao().obtenerTodas().any { it.nombre == "Nómina" })
        assertFalse(db.finanzasDao().configuracion()!!.onboardingCompleto)
        assertEquals(clave, seguro.obtenerPassphraseBd())
        assertTrue(seguro.validarPin("1234"))
        assertFalse(recibo.exists())
    }

    @Test fun importesInconsistentesOFueraDeRangoNoReemplazanLosDatos() = runBlocking {
        repo.exportar(Uri.fromFile(respaldo), password)
        val original = ArchivoRespaldo.leer(CodecRespaldo.descifrar(respaldo.readBytes(), password))
        listOf("montoCentavos" to 1L, "montoCentavos" to Long.MAX_VALUE, "monto" to Double.MAX_VALUE).forEach { (campo, valor) ->
            val modificado = original.copy(tablas = original.tablas.map { tabla ->
                if (tabla.nombre != "transacciones") tabla else {
                    val indice = tabla.columnas.indexOfFirst { it.nombre == campo }
                    tabla.copy(filas = tabla.filas.map { it.toMutableList().apply { this[indice] = valor } })
                }
            })
            respaldo.writeBytes(CodecRespaldo.cifrar(ArchivoRespaldo.escribir(modificado), password))
            assertTrue(runCatching { repo.restaurar(Uri.fromFile(respaldo), password) }.isFailure)
            assertEquals(2500050L, db.finanzasDao().transaccion(7)!!.montoCentavos)
            assertTrue(recibo.exists())
        }
    }
}
