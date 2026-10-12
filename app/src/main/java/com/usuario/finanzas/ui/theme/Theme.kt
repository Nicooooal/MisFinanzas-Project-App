package com.usuario.finanzas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF63D7AF),
    onPrimary = Color(0xFF003827),
    primaryContainer = VerdeOscuro,
    onPrimaryContainer = VerdeClaro,
    secondary = Color(0xFFA6CAB9),
    onSecondary = Color(0xFF18382A),
    secondaryContainer = Color(0xFF2F4C3D),
    onSecondaryContainer = Color(0xFFD2EBDC),
    tertiary = Color(0xFFE5B96C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF612D29),
    onErrorContainer = Color(0xFFFFDAD5),
    background = FondoOscuro,
    onBackground = TextoClaro,
    surface = SuperficieOscura,
    onSurface = TextoClaro,
    surfaceVariant = SuperficieElevada,
    onSurfaceVariant = TextoSecundario,
    outline = Color(0xFF81978B),
    outlineVariant = Color(0xFF354A3E)
)

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF087F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5E5),
    onPrimaryContainer = Color(0xFF07553D),
    secondary = Color(0xFF52675B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EFE8),
    onSecondaryContainer = Color(0xFF274435),
    tertiary = Color(0xFF826020),
    error = Color(0xFFB7463F),
    onError = Color.White,
    errorContainer = Color(0xFFFFE9E5),
    onErrorContainer = Color(0xFF87251F),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF192D22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF192D22),
    surfaceVariant = Color(0xFFEBF1ED),
    onSurfaceVariant = Color(0xFF53685B),
    outline = Color(0xFF778E7F),
    outlineVariant = Color(0xFFDFE8E2)
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinanzasTheme(tema: String = "sistema", content: @Composable () -> Unit) {
    val oscuro = tema == "oscuro" || (tema == "sistema" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        typography = Tipografia,
        shapes = Formas,
        content = content
    )
}
