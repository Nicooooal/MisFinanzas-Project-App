package com.usuario.finanzas.data.repository

import com.usuario.finanzas.data.local.dao.TransaccionPendienteDao
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import com.usuario.finanzas.domain.model.TransaccionDetectada
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendientesRepository @Inject constructor(
    private val dao: TransaccionPendienteDao
) {
    /** Flow con la lista completa de detecciones pendientes (más recientes primero). */
    val pendientes: Flow<List<TransaccionPendiente>> = dao.observarTodas()

    /** Flow con el número de pendientes (para el badge en la barra de navegación). */
    val conteo: Flow<Int> = dao.observarConteo()

    /** Guarda una nueva detección en la bandeja de pendientes. */
    suspend fun agregar(detectada: TransaccionDetectada): Int {
        return dao.insertar(
            TransaccionPendiente(
                monto = detectada.monto,
                comercio = detectada.comercio,
                fecha = detectada.fecha,
                tipo = detectada.tipo,
                banco = detectada.banco,
                textoOriginal = detectada.textoOriginal,
                direccion = detectada.direccion
            )
        ).toInt()
    }

    /** Obtiene una detección por id para mostrar en la pantalla de confirmación. */
    suspend fun obtenerPorId(id: Int): TransaccionPendiente? = dao.obtenerPorId(id)

    /** Elimina una detección (guardada o descartada). */
    suspend fun eliminar(id: Int) = dao.eliminar(id)
}
