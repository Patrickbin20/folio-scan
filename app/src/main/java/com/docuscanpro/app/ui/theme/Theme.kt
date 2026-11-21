package com.docuscanpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.docuscanpro.app.R

/*
 * Design « encre sur papier » : fond blanc cassé, cartes blanches à filets fins,
 * texte presque noir, un seul accent bleu réservé à l'action principale.
 */
val Ink = Color(0xFF0E1116)
val Paper = Color(0xFFF6F6F3)
val Line = Color(0xFFE3E3DE)
val Accent = Color(0xFF2F5BFF)
val Muted = Color(0xFF6B6E76)
// Alias conservés pour les écrans existants
val Navy = Ink
val NavyDeep = Ink
val Electric = Accent
val Success = Color(0xFF1F7A5C)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDE8), onPrimaryContainer = Ink,
    secondary = Accent, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF), onSecondaryContainer = Color(0xFF1B3FD1),
    tertiary = Accent,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFF1F1EC), onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F1EC), surfaceContainerHigh = Color(0xFFEDEDE8),
    surfaceContainerHighest = Color(0xFFE6E6E0),
    outline = Line, outlineVariant = Color(0xFFEDEDE8),
    scrim = Color.Black,
    error = Color(0xFFC62828)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFEDEEF0), onPrimary = Color(0xFF0D0E11),
    primaryContainer = Color(0xFF23262C), onPrimaryContainer = Color(0xFFEDEEF0),
    secondary = Color(0xFF4C72FF), onSecondary = Color.White,
    secondaryContainer = Color(0xFF1B2547), onSecondaryContainer = Color(0xFFD3DCFF),
    tertiary = Color(0xFF4C72FF),
    background = Color(0xFF0D0E11), onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF0D0E11), onSurface = Color(0xFFEDEEF0),
    surfaceVariant = Color(0xFF22252B), onSurfaceVariant = Color(0xFF9A9EA7),
    surfaceContainerLowest = Color(0xFF17191D), surfaceContainerLow = Color(0xFF17191D),
    surfaceContainer = Color(0xFF1C1F24), surfaceContainerHigh = Color(0xFF23262C),
    surfaceContainerHighest = Color(0xFF2A2D34),
    outline = Color(0xFF262930), outlineVariant = Color(0xFF23262C),
    scrim = Color.Black,
    error = Color(0xFFFF6B60)
)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun TextStyle.inter(weight: FontWeight? = null, size: Int? = null, spacing: Float? = null) = copy(
    fontFamily = InterFamily,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    letterSpacing = spacing?.sp ?: letterSpacing
)

private val AppTypography = Typography().let { t ->
    Typography(
        displaySmall = t.displaySmall.inter(FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.inter(FontWeight.SemiBold, spacing = -0.8f),
        headlineMedium = t.headlineMedium.inter(FontWeight.SemiBold, 28, -0.8f),
        headlineSmall = t.headlineSmall.inter(FontWeight.SemiBold, 24, -0.6f),
        titleLarge = t.titleLarge.inter(FontWeight.SemiBold, 20, -0.4f),
        titleMedium = t.titleMedium.inter(FontWeight.SemiBold, 17, -0.3f),
        titleSmall = t.titleSmall.inter(FontWeight.SemiBold, 15, -0.1f),
        bodyLarge = t.bodyLarge.inter(FontWeight.Normal, 16, 0f),
        bodyMedium = t.bodyMedium.inter(FontWeight.Normal, 14, 0f),
        bodySmall = t.bodySmall.inter(FontWeight.Normal, 13, 0f),
        labelLarge = t.labelLarge.inter(FontWeight.SemiBold, 15, 0f),
        labelMedium = t.labelMedium.inter(FontWeight.Medium, 13, 0f),
        labelSmall = t.labelSmall.inter(FontWeight.Medium, 11, 0.1f)
    )
}

@Composable
fun DocuScanTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}

val SectionTitle = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
