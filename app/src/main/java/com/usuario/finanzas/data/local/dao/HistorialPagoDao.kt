package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.usuario.finanzas.data.local.entities.HistorialPago
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialPagoDao {

    @Query("SELECT * FROM historial_pagos WHERE servicioId = :servicioId ORDER BY fechaPago DESC")
    fun observarPorServicio(servicioId: Int): Flow<List<HistorialPago>>

    @Insert
    suspend fun insertar(pago: HistorialPago): Long

    @Delete
    suspend fun eliminar(pago: HistorialPago)

    /** Cuenta pagos de un servicio desde un timestamp (inicio del ciclo actual). */
    @Query("SELECT COUNT(*) FROM historial_pagos WHERE servicioId = :servicioId AND fechaPago >= :desde")
    suspend fun contarPagosDesde(servicioId: Int, desde: Long): Int

    /** Intervalo cerrado-abierto: un pago futuro no salda el ciclo consultado. */
    @Query("SELECT COUNT(*) FROM historial_pagos WHERE servicioId = :servicioId AND fechaPago >= :desde AND fechaPago < :hastaExclusivo")
    suspend fun contarPagosEntre(servicioId: Int, desde: Long, hastaExclusivo: Long): Int
}
