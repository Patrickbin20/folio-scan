package com.docuscanpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF0B1F3A)
val NavyDeep = Color(0xFF07152A)
val Electric = Color(0xFF2F6BFF)
val Success = Color(0xFF12A150)

private val Light = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E9F6), onPrimaryContainer = Navy,
    secondary = Electric, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EDFF), onSecondaryContainer = Color(0xFF0E3BA6),
    tertiary = Electric,
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF12151A),
    surface = Color(0xFFF6F7F9), onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFE9ECF1), onSurfaceVariant = Color(0xFF5A6372),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F2F5), surfaceContainerHigh = Color(0xFFEAEDF1),
    surfaceContainerHighest = Color(0xFFE3E7EC),
    outline = Color(0xFFCDD3DC), outlineVariant = Color(0xFFE4E8EE),
    error = Color(0xFFD93025)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4C9FF), onPrimary = NavyDeep,
    primaryContainer = Color(0xFF1B2D4C), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF6B97FF), onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A2B52), onSecondaryContainer = Color(0xFFD6E2FF),
    tertiary = Color(0xFF6B97FF),
    background = Color(0xFF0D0F13), onBackground = Color(0xFFE8EAEF),
    surface = Color(0xFF0D0F13), onSurface = Color(0xFFE8EAEF),
    surfaceVariant = Color(0xFF1F232B), onSurfaceVariant = Color(0xFF9BA4B3),
    surfaceContainerLowest = Color(0xFF14171D), surfaceContainerLow = Color(0xFF161920),
    surfaceContainer = Color(0xFF1A1E25), surfaceContainerHigh = Color(0xFF21252D),
    surfaceContainerHighest = Color(0xFF282C35),
    outline = Color(0xFF3A404B), outlineVariant = Color(0xFF282D36),
    error = Color(0xFFFF6B60)
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun DocuScanTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}

val SectionTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
