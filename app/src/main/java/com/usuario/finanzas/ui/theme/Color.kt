package com.usuario.finanzas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

val Verde: Color @Composable get() = MaterialTheme.colorScheme.primary
val VerdeOscuro = Color(0xFF164C3D)
val VerdeClaro = Color(0xFFBAF4DD)
val Rojo: Color @Composable get() = MaterialTheme.colorScheme.error
val Naranja: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val FondoOscuro = Color(0xFF101916)
val SuperficieOscura = Color(0xFF18241F)
val SuperficieElevada = Color(0xFF23352D)
val TextoClaro = Color(0xFFE7F0EA)
val TextoSecundario = Color(0xFFBCCDC2)

// The balance card has the same high contrast treatment in both themes.
val FondoSaldo = Color(0xFF123D32)
val TextoSaldo = Color(0xFFF1FFF8)
val AcentoSaldo = Color(0xFFADECCF)
