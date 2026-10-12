package com.usuario.finanzas.domain.finanzas

import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

enum class PeriodoEstadistico(val etiqueta: String) {
    SEMANA("Semanal"), QUINCENA("Quincenal"), MES("Mensual"), TRIMESTRE("Trimestral"), SEMESTRE("Semestral");
    fun inicio(fecha: LocalDate): LocalDate = when(this) {
        SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        QUINCENA -> fecha.withDayOfMonth(if (fecha.dayOfMonth <= 15) 1 else 16)
        MES -> fecha.withDayOfMonth(1)
        TRIMESTRE -> fecha.withMonth(((fecha.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
        SEMESTRE -> fecha.withMonth(if (fecha.monthValue <= 6) 1 else 7).withDayOfMonth(1)
    }
    fun siguiente(inicio: LocalDate): LocalDate = when(this) {
        SEMANA -> inicio.plusWeeks(1)
        QUINCENA -> if (inicio.dayOfMonth == 1) inicio.withDayOfMonth(16) else inicio.plusMonths(1).withDayOfMonth(1)
        MES -> inicio.plusMonths(1)
        TRIMESTRE -> inicio.plusMonths(3)
        SEMESTRE -> inicio.plusMonths(6)
    }
    fun mover(fecha: LocalDate, pasos: Int): LocalDate = if (pasos > 0) siguiente(inicio(fecha)) else inicio(inicio(fecha).minusDays(1))
    fun rango(fecha: LocalDate, zona: ZoneId = ZoneId.systemDefault()): LongRange {
        val desde = inicio(fecha)
        return desde.atStartOfDay(zona).toInstant().toEpochMilli() until siguiente(desde).atStartOfDay(zona).toInstant().toEpochMilli()
    }
}
