package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.example.config.WhitelabelConfig

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
    val colorScheme = remember {
        darkColorScheme(
            primary = ElectricCyan,
            onPrimary = WhitelabelConfig.onPrimaryColor,
            primaryContainer = ElectricCyanContainer,
            onPrimaryContainer = ElectricCyan,
            secondary = UltravioletPulse,
            onSecondary = WhitelabelConfig.onSecondaryColor,
            secondaryContainer = UltravioletContainer,
            onSecondaryContainer = TextPrimary,
            tertiary = CinemaGold,
            onTertiary = WhitelabelConfig.onAccentColor,
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
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
