package com.usuario.finanzas.domain.captura

import com.usuario.finanzas.domain.finanzas.Dinero
import com.usuario.finanzas.domain.model.ClaseMovimiento
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class CandidatoTotalRecibo(val centavos: Long, val etiqueta: String, val contexto: String)
data class ResultadoRecibo(val entrada: EntradaInterpretada, val candidatosTotales: List<CandidatoTotalRecibo>)

/** Detecta importes declarados como total. Nunca suma productos, impuestos ni medios de pago. */
object ParserRecibo {
    private val total = Regex("^(gran\\s+total|total\\s+a\\s+pagar|total\\s+de\\s+la\\s+venta|total\\s+venta|valor\\s+a\\s+pagar|valor\\s+total|importe\\s+total|total\\s+factura|total)(?=\\s|[:=$]|$)")
    private val excluido = Regex("\\b(sub\\s*total|iva|impuestos?|base|descuentos?|ahorro|cambio|efectivo|recibido|pagado|propina|articulos?|productos?|unidades?|cantidad|puntos|cuotas?|saldo|devolucion)\\b")
    private val fecha = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\b")
    private val metadatos = Regex("^(nit\\b|rut\\b|dian\\b|resolucion\\b|responsable\\b|regimen\\b|factura\\b|documento\\b|recibo\\b|ticket\\b|fecha\\b|hora\\b|tel[.: ]|telefono\\b|celular\\b|cajer[oa]\\b|caja\\b|cliente\\b|vendedor\\b|direccion\\b|correo\\b|www\\.|https?://|bienvenid|gracias\\b|original\\b|copia\\b|cufe\\b|cude\\b)")
    private fun normal(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun analizar(texto: String, hoy: LocalDate = LocalDate.now()): ResultadoRecibo {
        require(texto.length <= 50_000) { "El texto es demasiado largo" }
        val lineas = texto.lines().map { it.trim().replace(Regex("[\\t ]+"), " ") }.filter(String::isNotBlank)
        val candidatos = mutableListOf<CandidatoTotalRecibo>()
        lineas.forEachIndexed { i, linea ->
            val limpia = normal(linea).trimStart('*', '-', '=', ' ')
            val etiqueta = total.find(limpia) ?: return@forEachIndexed
            val resto = limpia.substring(etiqueta.range.last + 1).trim(' ', ':', '=', '*')
            // TOTAL IVA, TOTAL EFECTIVO o TOTAL ARTÍCULOS no son el valor de la compra.
            if (excluido.containsMatchIn(resto)) return@forEachIndexed
            val importe = importe(resto)
            if (importe != null) candidatos += CandidatoTotalRecibo(importe, etiqueta.value.uppercase(Locale.ROOT), linea)
            else if (resto.isBlank() || resto in listOf("$", "cop", "cop $")) {
                val siguiente = lineas.getOrNull(i + 1)
                siguiente?.let(::importe)?.let { candidatos += CandidatoTotalRecibo(it, etiqueta.value.uppercase(Locale.ROOT), "$linea\n$siguiente") }
            }
        }
        val unicos = candidatos.distinctBy { it.centavos }
        val fechaRecibo = lineas.asSequence()
            .filterNot { Regex("\\b(vencimiento|resolucion|vigencia|autorizacion|desde|hasta)\\b").containsMatchIn(normal(it)) }
            .sortedByDescending { Regex("\\b(fecha|fec)\\b").containsMatchIn(normal(it)) }
            .mapNotNull { linea -> fecha.find(linea)?.value?.let(::parsearFecha) }.firstOrNull()
        val comercio = lineas.take(12).firstOrNull { linea ->
            val n = normal(linea)
            linea.count(Char::isLetter) >= 3 && !metadatos.containsMatchIn(n) && !total.containsMatchIn(n) &&
                !excluido.containsMatchIn(n) && !fecha.containsMatchIn(linea) && !Regex("^\\d").containsMatchIn(n) &&
                !Regex("\\b(nit|dian|cufe|cude)\\b").containsMatchIn(n)
        }?.take(120) ?: "Recibo"
        val aviso = when {
            lineas.isEmpty() -> "No se encontró texto legible. Prueba una foto enfocada, con buena luz y el recibo completo."
            unicos.isEmpty() -> "No se identificó el total. Revisa el texto leído o escribe el importe del recibo."
            unicos.size > 1 -> "Hay varios totales posibles. Selecciona el correcto antes de guardar."
            fechaRecibo == null -> "Revisa comercio y total. No se identificó la fecha; se propone hoy."
            fechaRecibo.isAfter(hoy) -> "La fecha leída está en el futuro. Corrígela antes de guardar."
            else -> "Revisa comercio, fecha y total del recibo"
        }
        return ResultadoRecibo(EntradaInterpretada(comercio, unicos.singleOrNull()?.centavos, fechaRecibo ?: hoy, ClaseMovimiento.COMPRA, aviso), unicos)
    }

    private fun parsearFecha(s: String): LocalDate? = runCatching {
        val formato = when { s.length >= 5 && s[4] == '-' -> "uuuu-MM-dd"; '/' in s -> "d/M/uuuu"; else -> "d-M-uuuu" }
        LocalDate.parse(s, DateTimeFormatter.ofPattern(formato).withResolverStyle(ResolverStyle.STRICT))
    }.getOrNull()

    private fun importe(texto: String): Long? {
        var valor = texto.trim().replace(Regex("(?i)\\b(?:COP|USD|EUR|BRL)\\b"), "").trim().trim('$', '€', ' ', ':', '=', '*')
        if (!valor.matches(Regex("[0-9]+(?:[., ]+[0-9]+)*"))) return null
        // Los espacios solo se aceptan como separadores de miles completos.
        if (' ' in valor) {
            if (!valor.matches(Regex("[0-9]{1,3}(?: [0-9]{3})+(?:[.,][0-9]{1,2})?"))) return null
            valor = valor.replace(" ", "")
        }
        if (valor.matches(Regex("[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?"))) valor = valor.replace(",", "")
        return Dinero.parsear(valor)?.takeIf { it > 0 }
    }
}
