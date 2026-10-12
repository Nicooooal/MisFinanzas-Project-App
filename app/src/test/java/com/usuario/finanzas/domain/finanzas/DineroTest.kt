package com.usuario.finanzas.domain.finanzas

import org.junit.Assert.*
import org.junit.Test

class DineroTest {
    @Test fun conservaCentavosConSeparadoresAceptados() {
        mapOf("1.234,56" to 123456L, "1234,56" to 123456L,
            "1234.56" to 123456L, "1.234" to 123400L,
            "1.234.567,89" to 123456789L, "0,01" to 1L,
            " 2 500,50 " to 250050L, "0" to 0L).forEach { (entrada, esperado) ->
            assertEquals(entrada, esperado, Dinero.parsear(entrada))
        }
    }

    @Test fun rechazaImportesAmbiguosInvalidosOExcesivos() {
        listOf("", "NaN", "Infinity", "1e5", "2 pesos", "$ 10", "1,234",
            "12.34,56", "1.23.45", "1234.567", "-1", "9000000000000,01").forEach {
            assertNull(it, Dinero.parsear(it))
        }
        assertEquals(Dinero.MAX_CENTAVOS, Dinero.parsear("9000000000000"))
        assertEquals(-123456L, Dinero.parsear("-1.234,56", permitirNegativo = true))
    }

    @Test fun editarYVolverALeerNoPierdeCentavos() {
        listOf(0L, 1L, 10L, 123456789L, Dinero.MAX_CENTAVOS, -999L).forEach {
            assertEquals(it, Dinero.parsear(Dinero.editable(it), permitirNegativo = true))
        }
    }

    @Test fun sumaMuchosValoresDecimalesSinAcumularError() {
        assertEquals(100000L, Dinero.sumar(List(10000) { Dinero.aCentavos(0.1) }))
        assertEquals(101L, Dinero.aCentavos(1.005))
        assertEquals(1001L, Dinero.aCentavos(10.005))
        assertEquals(0.01, Dinero.aUnidades(1), 0.0)
    }

    @Test fun desbordamientoNoSeConvierteEnSaldoIncorrecto() {
        assertThrows(ArithmeticException::class.java) { Dinero.sumar(listOf(Long.MAX_VALUE, 1L)) }
        assertThrows(IllegalArgumentException::class.java) { Dinero.aCentavos(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Dinero.aCentavos(Double.POSITIVE_INFINITY) }
    }
}
