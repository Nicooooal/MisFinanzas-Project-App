package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "historial_pagos",
    foreignKeys = [
        ForeignKey(
            entity = Servicio::class,
            parentColumns = ["id"],
            childColumns = ["servicioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("servicioId")]
)
data class HistorialPago(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val servicioId: Int,
    val monto: Double,
    /** Timestamp epoch en milisegundos */
    val fechaPago: Long,
    val comprobantePath: String? = null
)
