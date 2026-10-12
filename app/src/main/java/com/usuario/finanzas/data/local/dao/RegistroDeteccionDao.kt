package com.usuario.finanzas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.usuario.finanzas.data.local.entities.RegistroDeteccion

@Dao
interface RegistroDeteccionDao {
    @Query("SELECT EXISTS(SELECT 1 FROM registro_detecciones WHERE clave = :clave)")
    suspend fun existe(clave: String): Boolean

    @Query("SELECT clave FROM registro_detecciones WHERE coincidencia = :coincidencia AND canal != :canal AND canal != '' AND emparejada = 0 AND fecha BETWEEN :desde AND :hasta ORDER BY fecha ASC LIMIT 1")
    suspend fun buscarEnOtroCanal(coincidencia: String, canal: String, desde: Long, hasta: Long): String?

    @Query("UPDATE registro_detecciones SET emparejada = 1 WHERE clave = :clave")
    suspend fun emparejar(clave: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(registro: RegistroDeteccion): Long

    @Query("DELETE FROM registro_detecciones WHERE fecha < :antes")
    suspend fun depurar(antes: Long)
}
