package com.usuario.finanzas.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.usuario.finanzas.data.repository.ServicioRepository
import com.usuario.finanzas.services.NotificacionHelper
import com.usuario.finanzas.data.local.dao.FinanzasDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker diario (programado en FinanzasApp) que revisa los servicios con fecha
 * límite a 3 días o menos y estado distinto de "pagado", y envía un recordatorio.
 */
@HiltWorker
class RecordatorioWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val servicioRepository: ServicioRepository,
    private val notificacionHelper: NotificacionHelper,
    private val finanzasDao: FinanzasDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val proximos = servicioRepository.serviciosProximosAVencer(dias = 3)
        val moneda = finanzasDao.configuracion()?.moneda ?: "COP"
        proximos.forEach { (servicio, diasRestantes) ->
            notificacionHelper.notificarRecordatorio(servicio, diasRestantes, moneda)
        }
        return Result.success()
    }

    companion object {
        const val NOMBRE_TRABAJO = "recordatorios_servicios"
    }
}
