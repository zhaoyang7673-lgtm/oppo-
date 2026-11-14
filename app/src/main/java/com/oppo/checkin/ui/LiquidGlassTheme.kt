package com.oppo.checkin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class LiquidGlassPalette(
    val isDark: Boolean,
    val deepSpaceBase: Color,
    val ambientVioletOrb: Color,
    val ambientBlueOrb: Color,
    val primaryGlassSurface: Color,
    val primaryGlassHighlight: Color,
    val primaryGlassBorder: Color,
    val secondaryGlassSurface: Color,
    val secondaryGlassBorder: Color,
    val capsuleSurface: Color,
    val capsuleBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlueViolet: Color,
    val calmMintSuccess: Color,
    val warmAmberWarning: Color
)

private val DarkLiquidGlassPalette = LiquidGlassPalette(
    isDark = true,
    deepSpaceBase = Color(0xFF0A0D18),
    ambientVioletOrb = Color(0x385B4AE4),
    ambientBlueOrb = Color(0x2E2E7CF6),
    primaryGlassSurface = Color(0x29FFFFFF),
    primaryGlassHighlight = Color(0x47FFFFFF),
    primaryGlassBorder = Color(0x36FFFFFF),
    secondaryGlassSurface = Color(0x1AFFFFFF),
    secondaryGlassBorder = Color(0x22FFFFFF),
    capsuleSurface = Color(0x2EFFFFFF),
    capsuleBorder = Color(0x3DFFFFFF),
    textPrimary = Color(0xFFF4F6FC),
    textSecondary = Color(0xFFB6C0D6),
    textMuted = Color(0xFF7B859E),
    accentBlueViolet = Color(0xFF7C8CFF),
    calmMintSuccess = Color(0xFF5CE1A6),
    warmAmberWarning = Color(0xFFFFB86C)
)

private val LightLiquidGlassPalette = LiquidGlassPalette(
    isDark = false,
    deepSpaceBase = Color(0xFFEEF2FA),
    ambientVioletOrb = Color(0x267B6EF6),
    ambientBlueOrb = Color(0x244B96FF),
    primaryGlassSurface = Color(0xCCFFFFFF),
    primaryGlassHighlight = Color(0xF2FFFFFF),
    primaryGlassBorder = Color(0x99FFFFFF),
    secondaryGlassSurface = Color(0xA6FFFFFF),
    secondaryGlassBorder = Color(0x80FFFFFF),
    capsuleSurface = Color(0xD9FFFFFF),
    capsuleBorder = Color(0xB3FFFFFF),
    textPrimary = Color(0xFF121726),
    textSecondary = Color(0xFF4A556E),
    textMuted = Color(0xFF7C879E),
    accentBlueViolet = Color(0xFF4F5FEF),
    calmMintSuccess = Color(0xFF159E68),
    warmAmberWarning = Color(0xFFD97724)
)

val LocalLiquidGlassPalette = staticCompositionLocalOf { DarkLiquidGlassPalette }

private val DarkMaterialScheme = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    onPrimary = Color(0xFF0A0D18),
    primaryContainer = Color(0xFF1F2642),
    onPrimaryContainer = Color(0xFFE5E9FF),
    secondary = Color(0xFF5CE1A6),
    background = Color(0xFF0A0D18),
    surface = Color(0xFF131829),
    onSurface = Color(0xFFF4F6FC),
    error = Color(0xFFFFB86C)
)

private val LightMaterialScheme = lightColorScheme(
    primary = Color(0xFF4F5FEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E6FF),
    onPrimaryContainer = Color(0xFF121726),
    secondary = Color(0xFF159E68),
    background = Color(0xFFEEF2FA),
    surface = Color.White,
    onSurface = Color(0xFF121726),
    error = Color(0xFFD97724)
)

private val LiquidTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun LiquidGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkLiquidGlassPalette else LightLiquidGlassPalette
    val colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(LocalLiquidGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiquidTypography,
            content = content
        )
    }
}
