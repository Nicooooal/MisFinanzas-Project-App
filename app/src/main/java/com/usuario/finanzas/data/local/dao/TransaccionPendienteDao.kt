package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.usuario.finanzas.data.local.entities.TransaccionPendiente
import kotlinx.coroutines.flow.Flow

@Dao
interface TransaccionPendienteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(pendiente: TransaccionPendiente): Long

    @Query("SELECT * FROM transacciones_pendientes ORDER BY fecha DESC")
    fun observarTodas(): Flow<List<TransaccionPendiente>>

    @Query("SELECT COUNT(*) FROM transacciones_pendientes")
    fun observarConteo(): Flow<Int>

    @Query("SELECT * FROM transacciones_pendientes WHERE id = :id")
    suspend fun obtenerPorId(id: Int): TransaccionPendiente?

    @Query("DELETE FROM transacciones_pendientes WHERE id = :id")
    suspend fun eliminar(id: Int)

    @Query("DELETE FROM transacciones_pendientes")
    suspend fun eliminarTodas()
}
