package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.usuario.finanzas.data.local.entities.Categoria
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categorias ORDER BY nombre")
    fun observarTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias ORDER BY nombre")
    suspend fun obtenerTodas(): List<Categoria>

    @Query("SELECT * FROM categorias WHERE id = :id")
    suspend fun obtenerPorId(id: Int): Categoria?

    @Insert
    suspend fun insertar(categoria: Categoria): Long

    @Update
    suspend fun actualizar(categoria: Categoria)

    @Query("UPDATE categorias SET archivada = CASE WHEN nombre = 'Otros' OR id IN (:seleccionadas) THEN 0 ELSE 1 END")
    suspend fun aplicarSeleccionInicial(seleccionadas: List<Int>)

    @Query("SELECT * FROM categorias WHERE archivada = 0 AND (direccion = 'ambas' OR direccion = :direccion) ORDER BY nombre")
    fun observarActivas(direccion: String): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias WHERE archivada = 0 AND (direccion = 'ambas' OR direccion = :direccion) ORDER BY nombre")
    suspend fun obtenerActivas(direccion: String): List<Categoria>
}
