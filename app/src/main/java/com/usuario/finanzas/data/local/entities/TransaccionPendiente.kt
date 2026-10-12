package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Transacción detectada automáticamente (notificación/SMS) que aún NO ha sido
 * confirmada ni descartada por el usuario.
 *
 * Se persiste en BD para que NO se pierda si el usuario borra la notificación,
 * cierra la app, o reinicia el celular antes de revisarla.
 * El usuario la revisa desde PantallaPendientes cuando quiera.
 */
@Entity(tableName = "transacciones_pendientes")
data class TransaccionPendiente(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monto: Double,
    val comercio: String,
    val fecha: Long,
    val tipo: String,
    val banco: String,
    val textoOriginal: String,
    @ColumnInfo(defaultValue = "'salida'") val direccion: String = "salida"
)
