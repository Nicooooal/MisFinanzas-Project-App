package com.usuario.finanzas.domain.finanzas

import com.usuario.finanzas.data.local.entities.ConfiguracionFinanciera
import com.usuario.finanzas.data.local.entities.Transaccion
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalculadoraFinancieraTest {
    private fun movimiento(monto: Double, direccion: String, tipo: String,
        fecha: Long = 1000L, exacto: Long? = null) = Transaccion(
        monto = monto, categoriaId = 1, descripcion = "Caso de prueba",
        nombreComercio = "Comercio", fecha = fecha, fuente = "manual",
        direccion = direccion, tipo = tipo, montoCentavos = exacto
    )

    @Test fun saldoIncluyeInicialEIngresosYRestaSoloGastos() {
        val filas = listOf(
            movimiento(500.25, "entrada", "ingreso"),
            movimiento(200.10, "salida", "compra"),
            movimiento(50.0, "salida", "transferencia"),
            movimiento(10000.0, "entrada", "transferencia_propia"),
            movimiento(10000.0, "salida", "transferencia_propia"),
            movimiento(123.0, "entrada", "ingreso", fecha = 999),
            movimiento(456.0, "salida", "compra", fecha = 2001)
        )
        val resumen = CalculadoraFinanciera.resumen(filas,
            ConfiguracionFinanciera(saldoInicialCentavos = 10000, fechaInicial = 1000), hasta = 2000)
        assertEquals(50025L, resumen.ingresosCentavos)
        assertEquals(25010L, resumen.gastosCentavos)
        assertEquals(35015L, resumen.saldoCentavos)
    }

    @Test fun usaImporteExactoYAdaptaHistoricoSinCambiarMontoGuardado() {
        val historico = movimiento(10.005, "salida", "compra")
        val nuevo = movimiento(999.0, "salida", "compra", exacto = 1L)
        assertEquals(1001L, CalculadoraFinanciera.monto(historico))
        assertEquals(1L, CalculadoraFinanciera.monto(nuevo))
        assertEquals(1002L, CalculadoraFinanciera.total(listOf(historico, nuevo), "salida"))
        assertEquals(10.005, historico.monto, 0.0)
    }

    @Test fun sinMovimientosConservaSaldoInicialInclusoNegativo() {
        val resumen = CalculadoraFinanciera.resumen(emptyList(),
            ConfiguracionFinanciera(saldoInicialCentavos = -100L, fechaInicial = 0), hasta = 1)
        assertEquals(ResumenFinanciero(0, 0, -100), resumen)
    }

    @Test fun periodoMensualIncluyeElUltimoMilisegundoDeFebreroBisiesto() {
        val zona = ZoneId.of("America/Bogota")
        val rango = CalculadoraFinanciera.rangoPeriodo("mensual", LocalDate.of(2024, 2, 29), zona)
        val inicio = LocalDate.of(2024, 2, 1).atStartOfDay(zona).toInstant().toEpochMilli()
        val siguiente = LocalDate.of(2024, 3, 1).atStartOfDay(zona).toInstant().toEpochMilli()
        assertEquals(inicio, rango.first)
        assertEquals(siguiente - 1, rango.last)
        assertTrue(siguiente - 1 in rango)
        assertFalse(siguiente in rango)
    }

    @Test fun semanaDeEneroPuedeEmpezarEnElAnioAnterior() {
        val zona = ZoneId.of("America/Bogota")
        val rango = CalculadoraFinanciera.rangoPeriodo("semanal", LocalDate.of(2026, 1, 1), zona)
        assertEquals(LocalDate.of(2025, 12, 29), CalculadoraFinanciera.fechaLocal(rango.first, zona))
        assertEquals(LocalDate.of(2026, 1, 4), CalculadoraFinanciera.fechaLocal(rango.last, zona))
    }
}
