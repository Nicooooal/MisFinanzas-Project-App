package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.usuario.finanzas.data.local.entities.AprendizajeCategoria

@Dao
interface AprendizajeCategoriaDao {

    /** [nombreComercio] debe llegar ya normalizado (ver AprendizajeCategoriaRepository.normalizar). */
    @Query("SELECT * FROM aprendizaje_categorias WHERE nombreComercio = :nombreComercio")
    suspend fun obtenerPorComercio(nombreComercio: String): AprendizajeCategoria?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(item: AprendizajeCategoria): Long

    @Query(
        """
        UPDATE aprendizaje_categorias
        SET categoriaId = :categoriaId,
            vecesUsada = vecesUsada + 1,
            ultimaActualizacion = :timestamp
        WHERE nombreComercio = :nombreComercio
        """
    )
    suspend fun actualizarExistente(nombreComercio: String, categoriaId: Int, timestamp: Long)

    /**
     * Upsert: si el comercio no existe lo inserta (vecesUsada = 1); si ya existe,
     * incrementa vecesUsada y actualiza categoriaId y timestamp.
     */
    @Transaction
    suspend fun guardarOActualizar(item: AprendizajeCategoria) {
        val resultado = insertarSiNoExiste(item)
        if (resultado == -1L) {
            actualizarExistente(item.nombreComercio, item.categoriaId, item.ultimaActualizacion)
        }
    }
}
