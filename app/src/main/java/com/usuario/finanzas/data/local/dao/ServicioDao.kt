package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.usuario.finanzas.data.local.entities.Servicio
import kotlinx.coroutines.flow.Flow

@Dao
interface ServicioDao {

    @Query("SELECT * FROM servicios ORDER BY diaLimite")
    fun observarTodos(): Flow<List<Servicio>>

    @Query("SELECT * FROM servicios ORDER BY diaLimite")
    suspend fun obtenerTodos(): List<Servicio>

    @Query("SELECT * FROM servicios WHERE id = :id")
    fun observarPorId(id: Int): Flow<Servicio?>

    @Query("SELECT * FROM servicios WHERE id = :id")
    suspend fun obtenerPorId(id: Int): Servicio?

    @Insert
    suspend fun insertar(servicio: Servicio): Long

    @Update
    suspend fun actualizar(servicio: Servicio)

    @Delete
    suspend fun eliminar(servicio: Servicio)
}
