package com.usuario.finanzas.domain.finanzas

import com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera
import com.usuario.finanzas.data.local.entities.Transaccion
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ResumenFinanciero(val ingresosCentavos: Long, val gastosCentavos: Long, val saldoCentavos: Long)

object CalculadoraFinanciera {
    fun monto(transaccion: Transaccion): Long = transaccion.montoCentavos ?: Dinero.aCentavos(transaccion.monto)
    fun total(transacciones: List<Transaccion>, direccion: String): Long = Dinero.sumar(
        transacciones.filter { it.direccion == direccion && it.tipo != "transferencia_propia" }.map(::monto)
    )

    fun resumen(transacciones: List<Transaccion>, configuracion: ConfiguracionFinanciera, hasta: Long = System.currentTimeMillis()): ResumenFinanciero {
        val incluidos = transacciones.filter { it.fecha in configuracion.fechaInicial..hasta }
        val ingresos = total(incluidos, "entrada")
        val gastos = total(incluidos, "salida")
        return ResumenFinanciero(ingresos, gastos, Math.subtractExact(Math.addExact(configuracion.saldoInicialCentavos, ingresos), gastos))
    }

    fun rangoPeriodo(ciclo: String, hoy: LocalDate = LocalDate.now(), zona: ZoneId = ZoneId.systemDefault()): LongRange {
        val inicio = when(ciclo) {
            "semanal" -> hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            "anual" -> hoy.withDayOfYear(1)
            else -> hoy.withDayOfMonth(1)
        }
        val fin = when(ciclo) {
            "semanal" -> inicio.plusWeeks(1)
            "anual" -> inicio.plusYears(1)
            else -> inicio.plusMonths(1)
        }
        return inicio.atStartOfDay(zona).toInstant().toEpochMilli() until fin.atStartOfDay(zona).toInstant().toEpochMilli()
    }

    fun fechaLocal(millis: Long, zona: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(millis).atZone(zona).toLocalDate()
}
