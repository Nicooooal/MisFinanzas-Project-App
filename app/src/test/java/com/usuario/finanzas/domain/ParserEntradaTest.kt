package com.usuario.finanzas.domain

import com.usuario.finanzas.domain.captura.ParserEntrada
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ParserEntradaTest {
    @Test fun separaGastosYAbreviaturas() {
        val r = ParserEntrada.interpretar("Almuerzo 25 mil + Uber 12k")
        assertEquals(listOf(2_500_000L, 1_200_000L), r.map { it.centavos })
        assertEquals(listOf("Almuerzo", "Uber"), r.map { it.concepto })
    }
    @Test fun recibeDictadoYNoUsaFechasComoMontos() {
        val r = ParserEntrada.interpretar("Almuerzo veinticinco mil pesos 06/10/2026").single()
        assertEquals(2_500_000L, r.centavos)
        assertEquals(LocalDate.of(2026,10,6), r.fecha)
    }
    @Test fun tablaConEncabezadosNoGuardaFormulas() {
        val r = ParserEntrada.interpretar("Fecha\tConcepto\tMonto\tTipo\n06/10/2026\tSalario\t1.500.000\tingreso\n06/10/2026\tError\t=SUM(A1)\tgasto")
        assertEquals(150_000_000L, r[0].centavos)
        assertEquals("entrada", r[0].clase.direccion)
        assertNull(r[1].centavos)
    }
    @Test fun reciboExcluyeSubtotalEImpuestos() {
        val r = ParserEntrada.recibo("TIENDA\nSubtotal 10.000\nIVA 1.900\nTOTAL 11.900")
        assertEquals(1_190_000L, r.centavos)
        assertNull(ParserEntrada.recibo("Subtotal 10.000\nIVA 1.900").centavos)
        assertNull(ParserEntrada.recibo("TOTAL 11.900\nTOTAL 15.000").centavos)
    }
    @Test fun variosImportesExigenRevision() {
        assertNull(ParserEntrada.interpretar("Compra 25000 saldo 100000").single().centavos)
    }
}
