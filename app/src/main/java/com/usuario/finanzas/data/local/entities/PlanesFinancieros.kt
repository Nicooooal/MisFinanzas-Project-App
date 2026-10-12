package com.usuario.finanzas.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "configuracion_financiera")
data class ConfiguracionFinanciera(
    @PrimaryKey val id: Int = 1,
    val moneda: String = "COP",
    val saldoInicialCentavos: Long = 0,
    /** Saldo al comienzo de este día. Solo movimientos desde esta fecha cambian el saldo. */
    val fechaInicial: Long = 0,
    val onboardingCompleto: Boolean = false
)

@Entity(tableName = "presupuestos", foreignKeys = [ForeignKey(
    entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaId"]
)], indices = [Index("categoriaId")])
data class Presupuesto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val limiteCentavos: Long,
    val categoriaId: Int? = null,
    /** semanal, mensual o anual; períodos calendario. */
    val ciclo: String = "mensual",
    val activo: Boolean = true
)

@Entity(tableName = "metas")
data class Meta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val objetivoCentavos: Long,
    val archivada: Boolean = false
)

@Entity(tableName = "aportes_meta", foreignKeys = [ForeignKey(
    entity = Meta::class, parentColumns = ["id"], childColumns = ["metaId"]
)], indices = [Index("metaId")])
data class AporteMeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metaId: Long,
    /** Positivo reserva dinero; negativo libera dinero. No crea gasto ni ingreso. */
    val montoCentavos: Long,
    val fecha: Long,
    val nota: String = ""
)

@Entity(tableName = "recurrentes", foreignKeys = [ForeignKey(
    entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaId"]
)], indices = [Index("categoriaId")])
data class Recurrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val montoCentavos: Long,
    val categoriaId: Int,
    val direccion: String,
    /** diario, semanal, quincenal (cada 14 días), mensual o anual. */
    val frecuencia: String,
    val fechaInicio: Long,
    val activo: Boolean = true
)

@Entity(tableName = "ocurrencias_recurrentes", primaryKeys = ["recurrenteId", "fechaProgramada"],
    foreignKeys = [
        ForeignKey(entity = Recurrente::class, parentColumns = ["id"], childColumns = ["recurrenteId"]),
        ForeignKey(entity = Transaccion::class, parentColumns = ["id"], childColumns = ["transaccionId"], onDelete = ForeignKey.SET_NULL)
    ], indices = [Index("transaccionId")])
data class OcurrenciaRecurrente(
    val recurrenteId: Long,
    val fechaProgramada: Long,
    val transaccionId: Int?,
    val registradaEn: Long
)
