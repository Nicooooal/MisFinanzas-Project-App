package com.usuario.finanzas.domain.captura

import com.usuario.finanzas.domain.model.ClaseMovimiento
import com.usuario.finanzas.services.TransaccionParser
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EntradaInterpretada(val concepto: String, val centavos: Long?, val fecha: LocalDate, val clase: ClaseMovimiento, val advertencia: String? = null)

/** Solo genera borradores: nunca confirma movimientos ni interpreta un saldo como ingreso. */
object ParserEntrada {
    private val fechaRegex = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})\\b")
    private val importeRegex = Regex("(?<![\\w.,])(?:COP\\s*)?\\$?\\s*(-?\\d+(?:[.,]\\d+)*)\\s*(millones?|millon|mil|k|m)?(?=\\s|$|[;:)])", RegexOption.IGNORE_CASE)
    private val unidades = mapOf("cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300, "cuatrocientos" to 400, "quinientos" to 500, "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900)
    private val palabrasNumero = Regex("\\b(?:${unidades.keys.joinToString("|")}|mil)(?:\\s+(?:${unidades.keys.joinToString("|")}|mil|y))*\\b")
    private fun normalizar(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun interpretar(texto: String, hoy: LocalDate = LocalDate.now()): List<EntradaInterpretada> {
        require(texto.length <= 50_000) { "El texto es demasiado largo" }
        if ('\t' in texto) return tabla(texto, hoy)
        return texto.split(Regex("[+;\\n]+")).filter { it.isNotBlank() }.take(200).map { fragmento(it.trim(), hoy) }
    }

    private fun fecha(texto: String): LocalDate? = runCatching {
        if ('/' in texto) LocalDate.parse(texto, DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) else LocalDate.parse(texto)
    }.getOrNull()

    private fun fragmento(texto: String, hoy: LocalDate): EntradaInterpretada {
        val coincidenciaFecha = fechaRegex.find(texto)
        val fecha = coincidenciaFecha?.value?.let(::fecha) ?: hoy
        val sinFecha = texto.replace(fechaRegex, "").trim()
        val importes = importeRegex.findAll(sinFecha).toList()
        var concepto = sinFecha
        var monto: Long? = null
        var advertencia: String? = null
        if (importes.size == 1) {
            val m = importes.single()
            monto = cantidad(m.groupValues[1].removePrefix("-"), m.groupValues[2])
            concepto = sinFecha.removeRange(m.range).replace(Regex("(?i)\\b(?:pesos|COP)\\b"), "").trim(' ', ':', '-', '.')
        } else if (importes.isEmpty()) {
            val normal = normalizar(sinFecha)
            val palabras = palabrasNumero.findAll(normal).toList()
            if (palabras.size == 1) {
                var grupo = 0L
                var acumulado = 0L
                palabras.single().value.split(' ').forEach { palabra ->
                    if (palabra == "mil") { acumulado += grupo.coerceAtLeast(1) * 1000; grupo = 0 }
                    else grupo += unidades[palabra] ?: 0
                }
                monto = ((acumulado + grupo) * 100).takeIf { it > 0 }
                concepto = sinFecha.removeRange(palabras.single().range).replace(Regex("(?i)\\bpesos\\b"), "").trim()
                advertencia = "Revisa el importe interpretado del dictado"
            }
        } else advertencia = "Hay varios números; completa el importe correcto"
        if (coincidenciaFecha != null && fecha(coincidenciaFecha.value) == null) advertencia = "La fecha no es válida; selecciona una fecha"
        if (monto == null) advertencia = advertencia ?: "Completa el importe"
        return EntradaInterpretada(concepto.ifBlank { "Movimiento" }, monto, fecha, clase(texto), advertencia)
    }

    private fun clase(texto: String): ClaseMovimiento {
        val normal = normalizar(texto)
        return when {
            Regex("\\b(ingreso|recibi|recibido|salario|nomina|bono|freelance|income|salary)\\b").containsMatchIn(normal) -> ClaseMovimiento.INGRESO
            Regex("\\b(envie|envio|transferi)\\b").containsMatchIn(normal) -> ClaseMovimiento.ENVIO
            Regex("\\b(retiro|retire)\\b").containsMatchIn(normal) -> ClaseMovimiento.RETIRO
            else -> ClaseMovimiento.COMPRA
        }
    }

    private fun cantidad(numero: String, sufijo: String = ""): Long? = runCatching {
        val base = TransaccionParser.parsearMonto(numero) ?: return null
        val factor = when (normalizar(sufijo)) { "k", "mil" -> 1000; "m", "millon", "millones" -> 1_000_000; else -> 1 }
        BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(factor.toLong() * 100)).longValueExact().takeIf { it > 0 }
    }.getOrNull()

    /** Excel copia columnas separadas por tabuladores. No se ejecutan fórmulas. */
    private fun tabla(texto: String, hoy: LocalDate): List<EntradaInterpretada> {
        val filas = texto.lines().filter { it.isNotBlank() }.map { it.split('\t').map(String::trim) }
        if (filas.isEmpty()) return emptyList()
        val encabezado = filas.first().map(::normalizar)
        val iMonto = encabezado.indexOfFirst { it in listOf("monto", "importe", "valor", "amount") }
        val iConcepto = encabezado.indexOfFirst { it in listOf("concepto", "descripcion", "comercio", "description") }
        val iFecha = encabezado.indexOfFirst { it in listOf("fecha", "date") }
        val iTipo = encabezado.indexOfFirst { it in listOf("tipo", "direccion", "type") }
        if (iMonto < 0 || iConcepto < 0) return filas.take(200).map { fragmento(it.joinToString(" "), hoy) }
        return filas.drop(1).take(200).map { c ->
            val monto = c.getOrNull(iMonto).orEmpty().replace("$", "").replace("COP", "", true).trim()
            val fechaOriginal = c.getOrNull(iFecha)
            val f = fechaOriginal?.let(::fecha)
            val centavos = cantidad(monto.removePrefix("-"))
            EntradaInterpretada(c.getOrNull(iConcepto).orEmpty().ifBlank { "Movimiento" }, centavos, f ?: hoy,
                clase(c.getOrNull(iTipo).orEmpty() + " " + c.getOrNull(iConcepto).orEmpty()),
                when { centavos == null -> "Completa el importe"; fechaOriginal != null && f == null -> "La fecha no es válida; selecciona una fecha"; else -> null })
        }
    }

    fun recibo(texto: String, hoy: LocalDate = LocalDate.now()): EntradaInterpretada =
        ParserRecibo.analizar(texto, hoy).entrada
}
