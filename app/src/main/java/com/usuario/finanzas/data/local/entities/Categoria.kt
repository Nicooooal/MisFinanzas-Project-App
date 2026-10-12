package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    /** Color hex, ej. "#4CAF50" */
    val color: String,
    /** Nombre del ícono de Material Icons (ver IconosCategoria en ui/util/Formato.kt) */
    val icono: String,
    @ColumnInfo(defaultValue = "'ambas'") val direccion: String = "ambas",
    @ColumnInfo(defaultValue = "0") val archivada: Boolean = false
)
