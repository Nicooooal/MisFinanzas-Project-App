package com.usuario.finanzas.ui.util

import com.usuario.finanzas.services.TransaccionParser
import java.math.BigDecimal

fun leerMonto(texto: String): Double? = TransaccionParser.parsearMonto(texto)
    ?.takeIf { it.isFinite() && it > 0 }

fun montoEditable(monto: Double): String =
    if (monto.isFinite()) BigDecimal.valueOf(monto).stripTrailingZeros().toPlainString() else ""
