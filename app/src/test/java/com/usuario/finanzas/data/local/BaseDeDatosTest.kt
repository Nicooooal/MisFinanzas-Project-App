package com.usuario.finanzas.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.usuario.finanzas.data.local.entities.Categoria
import com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera
import com.usuario.finanzas.data.local.entities.Transaccion
import com.usuario.finanzas.data.repository.DeteccionRepository
import com.usuario.finanzas.data.repository.PendientesRepository
import com.usuario.finanzas.data.preferences.CoordinadorDatos
import com.usuario.finanzas.domain.model.TransaccionDetectada
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BaseDeDatosTest {
    private fun nuevaBase() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), FinanzasDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test fun migracionDesdeV4ValidaEsquemaRoomYConservaHistorico() = runBlocking {
        val contexto = RuntimeEnvironment.getApplication()
        val nombre = "migracion-${UUID.randomUUID()}.db"
        val archivo = contexto.getDatabasePath(nombre)
        archivo.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(archivo, null).use { anterior ->
            val fuente = javaClass.classLoader!!.getResourceAsStream("schema-v4.sql")!!
                .bufferedReader().use { it.readText() }.lineSequence()
                .filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
            fuente.split(';').map(String::trim).filter(String::isNotEmpty).forEach(anterior::execSQL)
            anterior.version = 4
        }
        val db = Room.databaseBuilder(contexto, FinanzasDatabase::class.java, nombre)
            .addMigrations(FinanzasDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            // Abrir mediante Room ejecuta también su validación completa del esquema destino.
            val historico = db.transaccionDao().listarEntre(0, 3000).single()
            assertEquals(7, historico.id)
            assertEquals(50000.5, historico.monto, 0.0)
            assertEquals(1, historico.categoriaId)
            assertEquals(4, historico.servicioIdRelacionado)
            assertEquals("Histórico con acentos: conexión", historico.descripcion)
            assertEquals("comprobantes/recibo.jpg", historico.comprobantePath)
            assertEquals("salida", historico.direccion)
            assertEquals("pago_servicio", historico.tipo)
            assertEquals("Nequi", historico.banco)
            assertNull(historico.montoCentavos)
            assertEquals("", historico.etiquetas)
            assertEquals("Servicios", db.categoriaDao().obtenerPorId(1)!!.nombre)
            assertEquals("ambas", db.categoriaDao().obtenerPorId(1)!!.direccion)
            assertFalse(db.categoriaDao().obtenerPorId(1)!!.archivada)
            assertEquals(com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera(), db.finanzasDao().configuracion())
            assertEquals("entrada", db.transaccionPendienteDao().obtenerPorId(8)!!.direccion)
            assertTrue(db.registroDeteccionDao().existe("origen-existente"))
            val sqlite = db.openHelper.readableDatabase
            listOf("servicios", "historial_pagos", "aprendizaje_categorias").forEach { tabla ->
                sqlite.query("SELECT COUNT(*) FROM $tabla").use {
                    assertTrue(it.moveToFirst()); assertEquals(tabla, 1, it.getInt(0))
                }
            }
            sqlite.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); contexto.deleteDatabase(nombre) }
    }

    @Test fun consultasSeparanIngresosGastosYTransferenciasPropiasEnLosLimites() = runBlocking {
        val db = nuevaBase()
        try {
            db.categoriaDao().insertar(Categoria(1, "Otros", "#999999", "Category"))
            val dao = db.transaccionDao()
            suspend fun agregar(monto: Double, direccion: String, tipo: String, fecha: Long) {
                dao.insertar(Transaccion(monto = monto, categoriaId = 1, descripcion = "Prueba",
                    nombreComercio = "Comercio", fecha = fecha, fuente = "manual",
                    direccion = direccion, tipo = tipo))
            }
            agregar(0.10, "salida", "compra", 1000)
            agregar(0.20, "salida", "compra", 2000)
            agregar(20.0, "entrada", "ingreso", 1500)
            agregar(500.0, "salida", "transferencia_propia", 1500)
            agregar(500.0, "entrada", "transferencia_propia", 1500)
            agregar(100.0, "salida", "compra", 999)
            agregar(100.0, "salida", "compra", 2001)
            assertEquals(0.30, dao.totalEntreUnaVez(1000, 2000), 0.000001)
            assertEquals(20.0, dao.entradasEntreUnaVez(1000, 2000), 0.0)
            assertEquals(0.30, dao.gastosPorCategoriaEntreUnaVez(1000, 2000).single().total, 0.000001)
            assertEquals(0.0, dao.totalEntreUnaVez(3000, 4000), 0.0)
            assertEquals(5, dao.listarEntre(1000, 2000).size)
        } finally { db.close() }
    }

    @Test fun avisosDuplicadosSeEmparejanUnoAUnoInclusoTrasConsumir() = runBlocking {
        val db = nuevaBase()
        try {
            val pendientes = PendientesRepository(db.transaccionPendienteDao())
            val detecciones = DeteccionRepository(pendientes, db, CoordinadorDatos())
            val ahora = System.currentTimeMillis()
            fun aviso(clave: String, canal: String, desplazamiento: Long = 0) = TransaccionDetectada(
                monto = 25000.0, comercio = "Ana", fecha = ahora + desplazamiento,
                tipo = "transferencia", banco = "Nequi", textoOriginal = "Enviaste $25000 a Ana",
                direccion = "salida", claveOrigen = clave, canal = canal
            )
            val primero = detecciones.publicar(aviso("sms-1", "SMS"))!!
            assertNull(detecciones.publicar(aviso("sms-1", "SMS")))
            assertNotNull(detecciones.publicar(aviso("sms-2", "SMS", 1)))
            assertNull(detecciones.publicar(aviso("notificacion-1", "NOTIFICACION", 2)))
            assertNull(detecciones.publicar(aviso("notificacion-2", "NOTIFICACION", 3)))
            assertNotNull(detecciones.publicar(aviso("notificacion-3", "NOTIFICACION", 4)))
            assertEquals(3, pendientes.pendientes.first().size)
            detecciones.consumir(primero)
            assertNull(detecciones.publicar(aviso("sms-1", "SMS")))
            assertEquals(2, pendientes.pendientes.first().size)
        } finally { db.close() }
    }

    @Test fun deteccionBancariaNoMezclaPesosConLaMonedaElegida() = runBlocking {
        val db = nuevaBase()
        try {
            val pendientes = PendientesRepository(db.transaccionPendienteDao())
            val detecciones = DeteccionRepository(pendientes, db, CoordinadorDatos())
            val aviso = TransaccionDetectada(monto = 25000.0, comercio = "Ana",
                fecha = System.currentTimeMillis(), tipo = "transferencia", banco = "Nequi",
                textoOriginal = "Recibiste $25000 de Ana", direccion = "entrada",
                claveOrigen = "sms-divisa", canal = "SMS")
            db.finanzasDao().guardarConfiguracion(ConfiguracionFinanciera(moneda = "USD"))
            assertNull(detecciones.publicar(aviso))
            assertTrue(pendientes.pendientes.first().isEmpty())
            assertFalse(db.registroDeteccionDao().existe("sms-divisa"))
            db.finanzasDao().guardarConfiguracion(ConfiguracionFinanciera(moneda = "COP"))
            assertNotNull(detecciones.publicar(aviso))
            assertEquals(1, pendientes.pendientes.first().size)
        } finally { db.close() }
    }
}
