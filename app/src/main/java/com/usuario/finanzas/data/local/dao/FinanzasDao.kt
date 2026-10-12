package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.usuario.finanzas.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanzasDao {
    @Query("SELECT * FROM configuracion_financiera WHERE id = 1")
    fun observarConfiguracion(): Flow<ConfiguracionFinanciera?>
    @Query("SELECT * FROM configuracion_financiera WHERE id = 1")
    suspend fun configuracion(): ConfiguracionFinanciera?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarConfiguracion(configuracion: ConfiguracionFinanciera)

    @Query("SELECT * FROM presupuestos ORDER BY activo DESC, nombre")
    fun observarPresupuestos(): Flow<List<Presupuesto>>
    @Insert suspend fun insertarPresupuesto(presupuesto: Presupuesto): Long
    @Update suspend fun actualizarPresupuesto(presupuesto: Presupuesto)
    @Query("SELECT * FROM metas ORDER BY archivada, nombre")
    fun observarMetas(): Flow<List<Meta>>
    @Query("SELECT * FROM metas WHERE id = :id")
    suspend fun meta(id: Long): Meta?
    @Insert suspend fun insertarMeta(meta: Meta): Long
    @Update suspend fun actualizarMeta(meta: Meta)
    @Query("SELECT * FROM aportes_meta ORDER BY fecha DESC, id DESC")
    fun observarAportes(): Flow<List<AporteMeta>>
    @Query("SELECT * FROM aportes_meta WHERE metaId = :metaId")
    suspend fun aportes(metaId: Long): List<AporteMeta>
    @Insert suspend fun insertarAporte(aporte: AporteMeta): Long

    @Query("SELECT * FROM recurrentes ORDER BY activo DESC, nombre")
    fun observarRecurrentes(): Flow<List<Recurrente>>
    @Query("SELECT * FROM recurrentes WHERE id = :id")
    suspend fun recurrente(id: Long): Recurrente?
    @Insert suspend fun insertarRecurrente(recurrente: Recurrente): Long
    @Update suspend fun actualizarRecurrente(recurrente: Recurrente)
    @Query("SELECT * FROM ocurrencias_recurrentes ORDER BY fechaProgramada DESC")
    fun observarOcurrencias(): Flow<List<OcurrenciaRecurrente>>
    @Query("SELECT * FROM ocurrencias_recurrentes WHERE recurrenteId = :id AND fechaProgramada = :fecha")
    suspend fun ocurrencia(id: Long, fecha: Long): OcurrenciaRecurrente?
    @Query("SELECT COUNT(*) FROM ocurrencias_recurrentes WHERE transaccionId = :id")
    suspend fun contarVinculos(id: Int): Int
    @Insert suspend fun insertarOcurrencia(ocurrencia: OcurrenciaRecurrente)
    @Query("SELECT * FROM transacciones WHERE id = :id")
    suspend fun transaccion(id: Int): Transaccion?
    @Query("SELECT (SELECT COUNT(*) FROM transacciones) + (SELECT COUNT(*) FROM transacciones_pendientes) + (SELECT COUNT(*) FROM presupuestos) + (SELECT COUNT(*) FROM metas) + (SELECT COUNT(*) FROM recurrentes) + (SELECT COUNT(*) FROM servicios) + (SELECT COUNT(*) FROM historial_pagos)")
    suspend fun contarDatosFinancieros(): Int
}
