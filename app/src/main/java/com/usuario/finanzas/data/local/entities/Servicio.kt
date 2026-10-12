package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servicios")
data class Servicio(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val montoEstimado: Double,
    /** Día del mes (1-31) en que llega/corta la factura */
    val diaCorte: Int,
    /** Día del mes (1-31) fecha límite de pago */
    val diaLimite: Int,
    /** "pendiente", "pagado" o "vencido" (se recalcula automáticamente) */
    val estado: String = ESTADO_PENDIENTE
) {
    companion object {
        const val ESTADO_PENDIENTE = "pendiente"
        const val ESTADO_PAGADO = "pagado"
        const val ESTADO_VENCIDO = "vencido"
    }
}
