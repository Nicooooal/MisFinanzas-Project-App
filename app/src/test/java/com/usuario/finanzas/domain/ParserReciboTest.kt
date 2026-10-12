package com.usuario.finanzas.domain

import com.usuario.finanzas.domain.captura.LineaRecibo
import com.usuario.finanzas.domain.captura.ParserRecibo
import com.usuario.finanzas.domain.captura.TextoGeometricoRecibo
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ParserReciboTest {
    private val hoy = LocalDate.of(2026, 10, 7)

    @Test fun leeTotalEnLaSiguienteLineaYFechaConGuiones() {
        val r = ParserRecibo.analizar("MERCADO CENTRAL\nNIT 900.123.456\nFECHA: 06-10-2026\nTOTAL A PAGAR\n$ 28.500,00\nEFECTIVO 30.000\nCAMBIO 1.500", hoy)
        assertEquals("MERCADO CENTRAL", r.entrada.concepto)
        assertEquals(2_850_000L, r.entrada.centavos)
        assertEquals(LocalDate.of(2026, 10, 6), r.entrada.fecha)
    }

    @Test fun reconoceEtiquetasUsualesDeComercios() {
        listOf("TOTAL VENTA", "VALOR A PAGAR", "VALOR TOTAL", "GRAN TOTAL", "TOTAL FACTURA", "TOTAL DE LA VENTA").forEach {
            assertEquals(it, 1_234_567L, ParserRecibo.analizar("TIENDA\n$it: COP 12.345,67", hoy).entrada.centavos)
        }
    }

    @Test fun separadoresAmericanosYMillarConEspacios() {
        assertEquals(1_234_550L, ParserRecibo.analizar("TOTAL 12,345.50", hoy).entrada.centavos)
        assertEquals(1_234_500L, ParserRecibo.analizar("TOTAL $12 345,00", hoy).entrada.centavos)
    }

    @Test fun noConfundeImpuestosMediosDePagoNiCantidadesConCompra() {
        val r = ParserRecibo.analizar("TIENDA\nSUBTOTAL 10.000\nTOTAL IVA 1.900\nTOTAL PRODUCTOS 2\nTOTAL EFECTIVO 20.000\nTOTAL CAMBIO 8.100\nTOTAL:11.900", hoy)
        assertEquals(1_190_000L, r.entrada.centavos)
        assertEquals(1, r.candidatosTotales.size)
    }

    @Test fun totalAisladoNoTomaElValorDeOtraEtiqueta() {
        val r = ParserRecibo.analizar("TOTAL\nEFECTIVO 50.000\nCAMBIO 10.000", hoy)
        assertNull(r.entrada.centavos)
        assertTrue(r.candidatosTotales.isEmpty())
    }

    @Test fun totalesDistintosExigenEleccionExplicita() {
        val r = ParserRecibo.analizar("TIENDA\nTOTAL 11.900\nTOTAL A PAGAR 12.000", hoy)
        assertNull(r.entrada.centavos)
        assertEquals(listOf(1_190_000L, 1_200_000L), r.candidatosTotales.map { it.centavos })
    }

    @Test fun totalesRepetidosEquivalentesNoSonConflicto() {
        val r = ParserRecibo.analizar("TIENDA\nTOTAL 11.900\nTOTAL A PAGAR $11.900,00", hoy)
        assertEquals(1_190_000L, r.entrada.centavos)
        assertEquals(1, r.candidatosTotales.size)
    }

    @Test fun noSumaArticulosNiUsaSubtotalComoTotal() {
        val r = ParserRecibo.analizar("TIENDA\nArroz 5.000\nLeche 6.900\nSUBTOTAL 11.900\nIVA 0", hoy)
        assertNull(r.entrada.centavos)
    }

    @Test fun noUsaNumeroFiscalComoComercioNiFechaDeResolucion() {
        val r = ParserRecibo.analizar("NIT 900123456\nDIAN\nFACTURA DE VENTA 815\nMERCADO DEL PARQUE\nResolución 12/01/2024\nFecha 07/10/2026\nTOTAL 11.900", hoy)
        assertEquals("MERCADO DEL PARQUE", r.entrada.concepto)
        assertEquals(hoy, r.entrada.fecha)
    }

    @Test fun fechaInvalidaNoSeAjustaSilenciosamente() {
        val r = ParserRecibo.analizar("TIENDA\nFecha 31-02-2026\nTOTAL 10.000", hoy)
        assertEquals(hoy, r.entrada.fecha)
        assertTrue(r.entrada.advertencia!!.contains("No se identificó la fecha"))
    }

    @Test fun noAceptaDosNumerosSueltosComoUnSoloImporte() {
        assertNull(ParserRecibo.analizar("TOTAL 3 25000", hoy).entrada.centavos)
        assertNull(ParserRecibo.analizar("TOTAL -25.000", hoy).entrada.centavos)
        assertNull(ParserRecibo.analizar("TOTAL 0", hoy).entrada.centavos)
    }

    @Test fun mensajeEspecificoParaImagenSinTexto() {
        val r = ParserRecibo.analizar("\n ", hoy)
        assertNull(r.entrada.centavos)
        assertTrue(r.entrada.advertencia!!.contains("No se encontró texto legible"))
    }

    @Test fun uneColumnaDeEtiquetasEImportesPorPosicionVisual() {
        // El orden entregado por el OCR agrupa primero textos y después importes.
        val texto = TextoGeometricoRecibo.ordenar(listOf(
            LineaRecibo("TIENDA", 0, 0, 90, 20),
            LineaRecibo("SUBTOTAL", 0, 50, 100, 70),
            LineaRecibo("TOTAL A PAGAR", 0, 100, 170, 120),
            LineaRecibo("EFECTIVO", 0, 150, 100, 170),
            LineaRecibo("10.000", 220, 51, 300, 71),
            LineaRecibo("11.900", 220, 103, 300, 123),
            LineaRecibo("20.000", 220, 149, 300, 169)
        ))
        assertEquals(1_190_000L, ParserRecibo.analizar(texto, hoy).entrada.centavos)
        assertTrue(texto.contains("TOTAL A PAGAR 11.900"))
    }

    @Test fun noUneLineasConSolapamientoHorizontal() {
        val texto = TextoGeometricoRecibo.ordenar(listOf(
            LineaRecibo("TOTAL", 0, 0, 90, 20),
            LineaRecibo("12.000", 0, 15, 90, 35)
        ))
        assertEquals("TOTAL\n12.000", texto)
    }
}
