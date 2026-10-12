package com.usuario.finanzas.data.repository

import android.app.Application
import androidx.room.Room
import com.usuario.finanzas.data.local.FinanzasDatabase
import com.usuario.finanzas.data.local.entities.*
import com.usuario.finanzas.domain.finanzas.Dinero
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FinanzasRepositoryTest {
    private lateinit var db: FinanzasDatabase
    private lateinit var repository: FinanzasRepository

    @Before fun abrirBase() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinanzasDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = FinanzasRepository(db, db.finanzasDao())
        db.categoriaDao().insertar(Categoria(1, "Otros", "#999999", "Category"))
        db.categoriaDao().insertar(Categoria(2, "Nómina", "#00AA00", "Category", direccion = "entrada"))
        db.categoriaDao().insertar(Categoria(3, "Archivada", "#999999", "Category", archivada = true))
        Unit
    }

    @After fun cerrarBase() = db.close()

    private suspend fun rechazar(accion: suspend () -> Unit) {
        val fallo = runCatching { accion() }.exceptionOrNull()
        assertTrue("Se esperaba validación, se obtuvo $fallo", fallo is IllegalArgumentException)
    }

    private suspend fun movimiento(direccion: String = "salida", tipo: String = "compra"): Int =
        db.transaccionDao().insertar(Transaccion(monto = 12.34, montoCentavos = 1234,
            categoriaId = 1, descripcion = "Movimiento real", nombreComercio = "Comercio",
            fecha = System.currentTimeMillis(), fuente = "automatico", direccion = direccion, tipo = tipo)).toInt()

    private val vencimiento: Long get() = LocalDate.now().minusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private suspend fun recurrente(): Long = db.finanzasDao().insertarRecurrente(Recurrente(
        nombre = "Internet", montoCentavos = 1234, categoriaId = 1, direccion = "salida",
        frecuencia = "mensual", fechaInicio = vencimiento))

    @Test fun aportesYRetiroNoCreanMovimientosYNoPermitenRetirarDeMas() = runBlocking {
        val id = db.finanzasDao().insertarMeta(Meta(nombre = "Viaje", objetivoCentavos = 100000))
        repository.registrarAporte(id, 10001)
        repository.registrarAporte(id, -1)
        rechazar { repository.registrarAporte(id, -10001) }
        rechazar { repository.registrarAporte(id, 0) }
        assertEquals(10000L, Dinero.sumar(db.finanzasDao().aportes(id).map { it.montoCentavos }))
        assertEquals(2, db.finanzasDao().aportes(id).size)
        assertTrue(db.transaccionDao().listarEntre(0, Long.MAX_VALUE).isEmpty())
        db.finanzasDao().actualizarMeta(db.finanzasDao().meta(id)!!.copy(archivada = true))
        rechazar { repository.registrarAporte(id, 100) }
        assertEquals(2, db.finanzasDao().aportes(id).size)
    }

    @Test fun confirmarUnVencimientoDosVecesNoDuplicaElCargo() = runBlocking {
        val id = recurrente()
        val fecha = vencimiento
        val transaccionId = repository.registrarOcurrencia(id, fecha)
        rechazar { repository.registrarOcurrencia(id, fecha) }
        val transaccion = db.transaccionDao().listarEntre(0, Long.MAX_VALUE).single()
        assertEquals(transaccionId, transaccion.id)
        assertEquals(1234L, transaccion.montoCentavos)
        assertEquals(12.34, transaccion.monto, 0.0)
        assertEquals(transaccionId, db.finanzasDao().ocurrencia(id, fecha)!!.transaccionId)
    }

    @Test fun vincularMovimientoExistenteNoLoDuplicaNiPermiteReutilizarlo() = runBlocking {
        val existente = movimiento()
        val primero = recurrente()
        val segundo = recurrente()
        assertEquals(existente, repository.registrarOcurrencia(primero, vencimiento, existente))
        rechazar { repository.registrarOcurrencia(segundo, vencimiento, existente) }
        assertEquals(1, db.transaccionDao().listarEntre(0, Long.MAX_VALUE).size)
        assertNull(db.finanzasDao().ocurrencia(segundo, vencimiento))
    }

    @Test fun recurrenciasRechazanIngresoOTransferenciaPropiaComoPago() = runBlocking {
        val id = recurrente()
        val ingreso = movimiento("entrada", "ingreso")
        val propia = movimiento(tipo = "transferencia_propia")
        rechazar { repository.registrarOcurrencia(id, vencimiento, ingreso) }
        rechazar { repository.registrarOcurrencia(id, vencimiento, propia) }
        assertNull(db.finanzasDao().ocurrencia(id, vencimiento))
        assertEquals(2, db.transaccionDao().listarEntre(0, Long.MAX_VALUE).size)
    }

    @Test fun categoriasIncompatiblesOArchivadasNoCreanPresupuestos() = runBlocking {
        rechazar { repository.guardarPresupuesto(Presupuesto(nombre = "Nómina", limiteCentavos = 1000, categoriaId = 2)) }
        rechazar { repository.guardarPresupuesto(Presupuesto(nombre = "Archivo", limiteCentavos = 1000, categoriaId = 3)) }
        rechazar { repository.guardarPresupuesto(Presupuesto(nombre = "Vacío", limiteCentavos = 0)) }
        assertTrue(repository.observarPresupuestos().first().isEmpty())
        repository.guardarPresupuesto(Presupuesto(nombre = "Total", limiteCentavos = 1000))
        assertNull(repository.observarPresupuestos().first().single().categoriaId)
    }

    @Test fun cambiarMonedaConImportesExistentesSeRechazaSinModificarConfiguracion() = runBlocking {
        val original = ConfiguracionFinanciera(moneda = "USD", fechaInicial = 0, onboardingCompleto = true)
        repository.guardarConfiguracion(original)
        movimiento()
        rechazar { repository.guardarConfiguracion(original.copy(moneda = "COP")) }
        assertEquals(original, db.finanzasDao().configuracion())
    }

    @Test fun planesConCategoriaRecategorizadaSePuedenPausarPeroNoReactivar() = runBlocking {
        repository.guardarPresupuesto(Presupuesto(nombre = "Servicios", limiteCentavos = 5000, categoriaId = 1))
        val presupuesto = repository.observarPresupuestos().first().single()
        val recurrenteId = recurrente()
        val recurrente = db.finanzasDao().recurrente(recurrenteId)!!
        db.categoriaDao().actualizar(db.categoriaDao().obtenerPorId(1)!!.copy(direccion = "entrada"))
        repository.guardarPresupuesto(presupuesto.copy(activo = false))
        repository.guardarRecurrente(recurrente.copy(activo = false))
        rechazar { repository.guardarPresupuesto(presupuesto.copy(activo = true)) }
        rechazar { repository.guardarRecurrente(recurrente.copy(activo = true)) }
        assertFalse(repository.observarPresupuestos().first().single().activo)
        assertFalse(db.finanzasDao().recurrente(recurrenteId)!!.activo)
    }
}
