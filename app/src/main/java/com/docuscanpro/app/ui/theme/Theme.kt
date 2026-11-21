package com.docuscanpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.docuscanpro.app.R

/*
 * Palette analogue apaisante : vert émeraude, vert sauge et bleu marine,
 * fusionnés dans un dégradé fluide pour les éléments clés (action principale, bouton Scanner, logo).
 */
val Emerald = Color(0xFF0F9B77)
val EmeraldLight = Color(0xFF3CC79A)
val Sage = Color(0xFF8FB09C)
val SageMist = Color(0xFFDCE7DF)
val Navy = Color(0xFF142B4A)
val NavyDeep = Color(0xFF0B1A2E)
val Ink = Color(0xFF12233A)
val Paper = Color(0xFFF4F7F4)
val Line = Color(0xFFDCE4DD)
val Accent = Emerald
val Muted = Color(0xFF5E6E69)
val Electric = Emerald
val Success = Emerald

/** Dégradé signature : sauge → émeraude → bleu marine. */
val AppGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to Color(0xFF7FB59A), 0.38f to Emerald, 0.72f to Color(0xFF1C5A6B), 1f to Navy)
)

private val Light = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = SageMist, onPrimaryContainer = Navy,
    secondary = Emerald, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3EA), onSecondaryContainer = Color(0xFF075E48),
    tertiary = Color(0xFF6F8F7C), onTertiary = Color.White,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFEAF0EB), onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF2EE), surfaceContainerHigh = Color(0xFFE5ECE7),
    surfaceContainerHighest = Color(0xFFDDE6E0),
    outline = Line, outlineVariant = Color(0xFFE8EEE9),
    scrim = Color.Black,
    error = Color(0xFFC0392B)
)

private val Dark = darkColorScheme(
    primary = EmeraldLight, onPrimary = Color(0xFF062A20),
    primaryContainer = Color(0xFF16302B), onPrimaryContainer = Color(0xFFD5F2E6),
    secondary = EmeraldLight, onSecondary = Color(0xFF062A20),
    secondaryContainer = Color(0xFF133B33), onSecondaryContainer = Color(0xFFCDEFE2),
    tertiary = Sage, onTertiary = Color(0xFF13241B),
    background = Color(0xFF0B1624), onBackground = Color(0xFFE6EEEA),
    surface = Color(0xFF0B1624), onSurface = Color(0xFFE6EEEA),
    surfaceVariant = Color(0xFF172739), onSurfaceVariant = Color(0xFF9AB0A6),
    surfaceContainerLowest = Color(0xFF112032), surfaceContainerLow = Color(0xFF112032),
    surfaceContainer = Color(0xFF15263A), surfaceContainerHigh = Color(0xFF1A2D43),
    surfaceContainerHighest = Color(0xFF20354D),
    outline = Color(0xFF1F3247), outlineVariant = Color(0xFF1A2B3F),
    scrim = Color.Black,
    error = Color(0xFFFF7A6B)
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
