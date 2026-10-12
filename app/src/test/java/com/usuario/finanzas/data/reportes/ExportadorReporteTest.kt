package com.usuario.finanzas.data.reportes

import android.app.Application
import android.net.Uri
import com.usuario.finanzas.data.local.entities.Transaccion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExportadorReporteTest {
    private val contexto get() = RuntimeEnvironment.getApplication()
    private fun archivo() = File(contexto.cacheDir, "reporte-${UUID.randomUUID()}.csv")
    private fun movimiento(nombre: String, dia: Int = 1) = Transaccion(
        id = dia, monto = 999.0, montoCentavos = 1234, categoriaId = 3,
        descripcion = "Detalle", nombreComercio = nombre,
        fecha = LocalDate.of(2026, 10, dia).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        fuente = "manual", direccion = "entrada", tipo = "ingreso", etiquetas = "hogar,niñez")

    @Test fun csvUsaUtf8ComasComillasFechaOrdenadaYCentavosDeLaMonedaElegida() = runBlocking {
        val archivo = archivo()
        try {
            ExportadorReporte(contexto).csv(Uri.fromFile(archivo), listOf(
                movimiento("Café, \"Niño\"\nPiso 2", 2), movimiento("Ingreso", 1)), "EUR")
            val bytes = archivo.readBytes()
            assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3).toByteArray())
            assertEquals(
                "\uFEFFFecha,Concepto,Direccion,Tipo,Monto,Moneda,CategoriaId,Etiquetas\r\n" +
                    "\"2026-10-01\",\"Ingreso\",\"entrada\",\"ingreso\",\"12.34\",\"EUR\",\"3\",\"hogar,niñez\"\r\n" +
                    "\"2026-10-02\",\"Café, \"\"Niño\"\"\nPiso 2\",\"entrada\",\"ingreso\",\"12.34\",\"EUR\",\"3\",\"hogar,niñez\"\r\n",
                bytes.toString(Charsets.UTF_8))
        } finally { archivo.delete() }
        Unit
    }

    @Test fun neutralizaFormulasTambienDespuesDeEspaciosYControles() = runBlocking {
        val archivo = archivo()
        val peligrosos = listOf("=SUM(1,2)", "+42", "-42", "@SUM(A1)", "  =1+1", "\t@SUM(A1)", "\r\n +42", "\u0001-1")
        try {
            ExportadorReporte(contexto).csv(Uri.fromFile(archivo),
                peligrosos.mapIndexed { indice, valor -> movimiento(valor, indice + 1) } + movimiento("  Compra", 12), "USD")
            val texto = archivo.readText(Charsets.UTF_8)
            peligrosos.forEach { valor -> assertTrue("Debe neutralizar $valor", texto.contains("\"'$valor\"")) }
            assertTrue(texto.contains("\"  Compra\""))
            assertFalse(texto.contains("\"'  Compra\""))
        } finally { archivo.delete() }
        Unit
    }

    @Test fun reporteVacioNoCreaArchivo() = runBlocking {
        val archivo = archivo()
        try {
            assertTrue(runCatching { ExportadorReporte(contexto).csv(Uri.fromFile(archivo), emptyList(), "COP") }.isFailure)
            assertFalse(archivo.exists())
        } finally { archivo.delete() }
        Unit
    }
}
