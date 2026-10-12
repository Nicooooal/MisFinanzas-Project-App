package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transacciones",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"]
        ),
        ForeignKey(
            entity = Servicio::class,
            parentColumns = ["id"],
            childColumns = ["servicioIdRelacionado"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoriaId"), Index("servicioIdRelacionado"), Index("fecha")]
)
data class Transaccion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monto: Double,
    val categoriaId: Int,
    val descripcion: String,
    val nombreComercio: String,
    /** Timestamp epoch en milisegundos */
    val fecha: Long,
    /** "manual" o "automatico" */
    val fuente: String,
    /** Ruta de la imagen del comprobante en almacenamiento interno, o null */
    val comprobantePath: String? = null,
    /** FK opcional al servicio que paga esta transacción */
    val servicioIdRelacionado: Int? = null,
    @ColumnInfo(defaultValue = "'salida'") val direccion: String = "salida",
    @ColumnInfo(defaultValue = "'compra'") val tipo: String = "compra",
    @ColumnInfo(defaultValue = "''") val banco: String = "",
    /** Importe exacto. Los registros históricos conservan monto como adaptador. */
    val montoCentavos: Long? = null,
    @ColumnInfo(defaultValue = "''") val etiquetas: String = ""
) {
    companion object {
        const val FUENTE_MANUAL = "manual"
        const val FUENTE_AUTOMATICO = "automatico"
    }
}
