package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NovaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = UltravioletPulse,
    onSecondary = TextPrimary,
    secondaryContainer = UltravioletContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = CinemaGold,
    onTertiary = ObsidianBg,
    tertiaryContainer = CinemaGoldContainer,
    onTertiaryContainer = CinemaGold,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateContainerHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateContainerLow,
    surfaceContainer = SlateContainer,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = SlateContainerHighest,
    outline = GlassBorder,
    outlineVariant = TextMuted,
    error = BroadcastCrimson,
    onError = TextPrimary,
    errorContainer = BroadcastCrimsonContainer,
    onErrorContainer = BroadcastCrimson
)

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NovaStreamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaDarkColorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
