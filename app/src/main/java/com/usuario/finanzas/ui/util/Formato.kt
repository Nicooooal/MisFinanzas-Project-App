package com.usuario.finanzas.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatoFinanciero {
    @Volatile var moneda: String = "COP"
}

/** Conserva la moneda configurada y la precisión monetaria del registro. */
fun formatearMonto(valor: Double, moneda: String = FormatoFinanciero.moneda): String =
    com.usuario.finanzas.domain.finanzas.Dinero.formatear(com.usuario.finanzas.domain.finanzas.Dinero.aCentavos(valor), moneda)

fun formatearFecha(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

fun formatearFechaHora(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/** Convierte el nombre de ícono guardado en la BD al ImageVector de Material Icons. */
fun iconoCategoria(nombre: String): ImageVector = when (nombre) {
    "Restaurant" -> Icons.Filled.Restaurant
    "DirectionsCar" -> Icons.Filled.DirectionsCar
    "Movie" -> Icons.Filled.Movie
    "LocalHospital" -> Icons.Filled.LocalHospital
    "Home" -> Icons.Filled.Home
    "ReceiptLong" -> Icons.Filled.ReceiptLong
    else -> Icons.Filled.Category
}

/** Convierte un color hex de la BD ("#4CAF50") a Color de Compose. */
fun colorDesdeHex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Gray
}
