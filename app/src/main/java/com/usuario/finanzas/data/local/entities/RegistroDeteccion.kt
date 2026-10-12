package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Solo huellas; se conserva tras confirmar o descartar para no repetir el aviso. */
@Entity(tableName = "registro_detecciones", indices = [Index("fecha")])
data class RegistroDeteccion(
    @PrimaryKey val clave: String,
    val coincidencia: String,
    val canal: String,
    val fecha: Long,
    val emparejada: Boolean = false
)
