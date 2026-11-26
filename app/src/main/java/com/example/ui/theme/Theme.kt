package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = IndustrialNavy,
    onPrimary = Color.White,
    primaryContainer = IndustrialNavyContainer,
    onPrimaryContainer = OnIndustrialNavyContainer,
    secondary = GasTeal,
    onSecondary = Color.White,
    secondaryContainer = GasTealContainer,
    onSecondaryContainer = OnGasTealContainer,
    tertiary = OverTargetAmber,
    onTertiary = Color.White,
    tertiaryContainer = ExcelPillAmber,
    onTertiaryContainer = ExcelPillInk,
    error = AlarmCrimson,
    onError = Color.White,
    errorContainer = AlarmCrimsonSoft,
    onErrorContainer = Color(0xFF7B0000),
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE3E8F2),
)

private val DarkColorScheme = darkColorScheme(
    primary = IndustrialNavyNight,
    onPrimary = IndustrialNavyDark,
    primaryContainer = IndustrialNavy,
    onPrimaryContainer = Color(0xFFE3ECFC),
    secondary = GasTealNight,
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF0B4D42),
    onSecondaryContainer = Color(0xFFC2F7EB),
    tertiary = Color(0xFFFFCC66),
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = Color(0xFF3D3418),
    onTertiaryContainer = Color(0xFFFFEB99),
    error = AlarmCrimsonNight,
    onError = Color(0xFF5A0000),
    errorContainer = Color(0xFF4A1418),
    onErrorContainer = Color(0xFFFFD6D6),
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF213049),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun GasSheetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
