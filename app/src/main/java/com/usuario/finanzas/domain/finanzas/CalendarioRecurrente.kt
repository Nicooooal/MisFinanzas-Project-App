package com.usuario.finanzas.domain.finanzas

import com.usuario.finanzas.data.local.entities.Recurrente
import java.time.LocalDate
import java.time.ZoneId

object CalendarioRecurrente {
    val frecuencias = listOf("diario", "semanal", "quincenal", "mensual", "anual")

    /** Se calcula siempre desde el ancla: enero 31 → febrero 28 → marzo 31. */
    fun fecha(inicio: LocalDate, frecuencia: String, indice: Long): LocalDate {
        require(indice >= 0)
        return when (frecuencia) {
            "diario" -> inicio.plusDays(indice)
            "semanal" -> inicio.plusWeeks(indice)
            "quincenal" -> inicio.plusWeeks(Math.multiplyExact(indice, 2))
            "mensual" -> inicio.plusMonths(indice)
            "anual" -> inicio.plusYears(indice)
            else -> throw IllegalArgumentException("Frecuencia no válida.")
        }
    }

    fun vencimientos(recurrente: Recurrente, hasta: LocalDate = LocalDate.now(), zona: ZoneId = ZoneId.systemDefault()): List<Long> {
        val inicio = CalculadoraFinanciera.fechaLocal(recurrente.fechaInicio, zona)
        if (!recurrente.activo || inicio > hasta) return emptyList()
        return generateSequence(0L) { it + 1 }.map { fecha(inicio, recurrente.frecuencia, it) }
            .takeWhile { it <= hasta }.take(100_000)
            .map { it.atStartOfDay(zona).toInstant().toEpochMilli() }.toList()
    }
}
