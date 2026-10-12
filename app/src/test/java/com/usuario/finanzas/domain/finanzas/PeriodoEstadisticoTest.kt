package com.usuario.finanzas.domain.finanzas
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
class PeriodoEstadisticoTest {
    @Test fun quincenasNoSeSolapanEnFebreroBisiesto() {
        val p = PeriodoEstadistico.QUINCENA
        val z = ZoneId.of("America/Bogota")
        val a = p.rango(LocalDate.of(2024,2,15), z)
        val b = p.rango(LocalDate.of(2024,2,29), z)
        assertEquals(a.last + 1, b.first)
        assertEquals(LocalDate.of(2024,3,1), p.siguiente(LocalDate.of(2024,2,16)))
    }
    @Test fun intervalosCalendarioYCambioAnio() {
        assertEquals(LocalDate.of(2026,10,1), PeriodoEstadistico.TRIMESTRE.inicio(LocalDate.of(2026,12,31)))
        assertEquals(LocalDate.of(2027,1,1), PeriodoEstadistico.SEMESTRE.mover(LocalDate.of(2026,10,6), 1))
        assertEquals(LocalDate.of(2026,10,5), PeriodoEstadistico.SEMANA.inicio(LocalDate.of(2026,10,6)))
    }
}
