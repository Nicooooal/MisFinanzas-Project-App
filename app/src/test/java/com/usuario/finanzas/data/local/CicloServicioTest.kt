package com.usuario.finanzas.data.local

import android.app.Application
import androidx.room.Room
import com.usuario.finanzas.data.local.entities.HistorialPago
import com.usuario.finanzas.data.local.entities.Servicio
import com.usuario.finanzas.data.repository.ServicioRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CicloServicioTest {
    @Test fun registrarPagoNoSobrescribeDatosActualizadosDelServicio() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinanzasDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo = ServicioRepository(db.servicioDao(), db.historialPagoDao(), db)
            val anterior = Servicio(1, "Agua", 100.0, 1, 15)
            db.servicioDao().insertar(anterior)
            db.servicioDao().actualizar(anterior.copy(nombre = "Agua actualizado", montoEstimado = 200.0))
            repo.registrarPago(anterior, 100.0)
            val actual = db.servicioDao().obtenerPorId(1)!!
            assertEquals("Agua actualizado", actual.nombre)
            assertEquals(200.0, actual.montoEstimado, 0.0)
            assertEquals(Servicio.ESTADO_PAGADO, actual.estado)
        } finally { db.close() }
    }

    @Test fun pagoFuturoOPrevioNoSaldaCicloActualYElDiaDeCorteEsInclusivo() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinanzasDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo = ServicioRepository(db.servicioDao(), db.historialPagoDao(), db)
            val servicio = Servicio(1, "Agua", 100.0, 25, 5)
            db.servicioDao().insertar(servicio)
            fun fecha(dia: String) = LocalDate.parse(dia).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.historialPagoDao().insertar(HistorialPago(servicioId = 1, monto = 100.0, fechaPago = fecha("2026-09-24")))
            db.historialPagoDao().insertar(HistorialPago(servicioId = 1, monto = 100.0, fechaPago = fecha("2026-10-25")))
            assertEquals(Servicio.ESTADO_VENCIDO, repo.calcularEstado(servicio, LocalDate.parse("2026-10-06")))
            db.historialPagoDao().insertar(HistorialPago(servicioId = 1, monto = 100.0, fechaPago = fecha("2026-09-25")))
            assertEquals(Servicio.ESTADO_PAGADO, repo.calcularEstado(servicio, LocalDate.parse("2026-10-06")))
            val sinPago = Servicio(2, "Internet", 100.0, 31, 5)
            assertEquals(5L, repo.diasHastaLimite(sinPago, LocalDate.parse("2026-02-28")))
        } finally { db.close() }
    }
}
