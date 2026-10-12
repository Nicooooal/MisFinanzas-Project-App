package com.usuario.finanzas.domain.finanzas

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Único adaptador entre los importes históricos y las unidades menores exactas. */
object Dinero {
    const val MAX_CENTAVOS = 900_000_000_000_000L

    fun aCentavos(unidades: Double): Long {
        require(unidades.isFinite()) { "El monto debe ser un número válido." }
        return BigDecimal.valueOf(unidades).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun aUnidades(centavos: Long): Double = BigDecimal.valueOf(centavos, 2).toDouble()

    /** Admite 1.234,56, 1234,56 o 1234.56, sin aceptar texto ni más de dos decimales. */
    fun parsear(texto: String, permitirNegativo: Boolean = false): Long? = runCatching {
        val entrada = texto.trim().replace(" ", "").replace("\u00A0", "")
        require(entrada.matches(Regex("-?[0-9]+([.,][0-9]+)*")))
        val valor = when {
            entrada.contains(',') -> {
                require(entrada.count { it == ',' } == 1)
                val partes = entrada.split(',')
                require(partes[1].length in 1..2)
                require(!partes[0].contains('.') || partes[0].matches(Regex("-?[0-9]{1,3}(\\.[0-9]{3})+")))
                partes[0].replace(".", "") + "." + partes[1]
            }
            entrada.contains('.') -> {
                if (entrada.matches(Regex("-?[0-9]{1,3}(\\.[0-9]{3})+"))) entrada.replace(".", "")
                else {
                    require(entrada.count { it == '.' } == 1 && entrada.substringAfter('.').length in 1..2)
                    entrada
                }
            }
            else -> entrada
        }
        BigDecimal(valor).movePointRight(2).longValueExact().also {
            require(it in -MAX_CENTAVOS..MAX_CENTAVOS && (permitirNegativo || it >= 0))
        }
    }.getOrNull()

    fun formatear(centavos: Long, moneda: String = "COP"): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = if (centavos % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(centavos, 2)) + " " + moneda

    fun editable(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString().replace('.', ',')
    fun sumar(valores: Iterable<Long>): Long = valores.fold(0L, Math::addExact)
}
