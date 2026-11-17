package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PitWallDarkColorScheme = darkColorScheme(
    primary = F1Red,
    onPrimary = TextPrimary,
    primaryContainer = F1RedDark,
    onPrimaryContainer = TextPrimary,
    secondary = TelemetryCyan,
    onSecondary = CarbonBlack,
    secondaryContainer = AsphaltElevated,
    onSecondaryContainer = TelemetryCyan,
    tertiary = SectorPurple,
    onTertiary = TextPrimary,
    background = CarbonBlack,
    onBackground = TextPrimary,
    surface = AsphaltSurface,
    onSurface = TextPrimary,
    surfaceVariant = AsphaltElevated,
    onSurfaceVariant = TextSecondary,
    outline = AsphaltBorder,
    outlineVariant = AsphaltCard,
    error = CriticalRed,
    onError = TextPrimary,
)

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun ApexTelemetryTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PitWallDarkColorScheme,
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content,
    )
}
