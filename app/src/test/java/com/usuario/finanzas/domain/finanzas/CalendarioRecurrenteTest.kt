package com.usuario.finanzas.domain.finanzas

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarioRecurrenteTest {
    @Test fun finDeMesSeRecuperaTrasFebrero() {
        val ancla = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), CalendarioRecurrente.fecha(ancla, "mensual", 1))
        assertEquals(LocalDate.of(2026, 3, 31), CalendarioRecurrente.fecha(ancla, "mensual", 2))
        assertEquals(LocalDate.of(2026, 4, 30), CalendarioRecurrente.fecha(ancla, "mensual", 3))
    }

    @Test fun aniversarioBisiestoRecuperaEl29DeFebrero() {
        val ancla = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), CalendarioRecurrente.fecha(ancla, "anual", 1))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarioRecurrente.fecha(ancla, "anual", 4))
    }

    @Test fun semanalYQuincenalCruzanAnioConIntervalosConstantes() {
        val ancla = LocalDate.of(2026, 12, 28)
        assertEquals(LocalDate.of(2026, 12, 29), CalendarioRecurrente.fecha(ancla, "diario", 1))
        assertEquals(LocalDate.of(2027, 1, 4), CalendarioRecurrente.fecha(ancla, "semanal", 1))
        assertEquals(LocalDate.of(2027, 1, 11), CalendarioRecurrente.fecha(ancla, "quincenal", 1))
        assertEquals(LocalDate.of(2027, 1, 25), CalendarioRecurrente.fecha(ancla, "quincenal", 2))
    }

    @Test fun configuracionInvalidaNoGeneraCargos() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarioRecurrente.fecha(LocalDate.of(2026, 1, 1), "invalida", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarioRecurrente.fecha(LocalDate.of(2026, 1, 1), "mensual", -1)
        }
    }
}
