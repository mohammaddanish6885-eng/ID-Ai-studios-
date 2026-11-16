package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FrameCraftDarkColorScheme = darkColorScheme(
    primary = CoralEmber,
    onPrimary = Color.White,
    primaryContainer = CoralContainer,
    onPrimaryContainer = OnCoralContainer,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF003733),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = WarmAmber,
    onTertiary = Color(0xFF331E00),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianVoid,
    onBackground = StudioTextPrimary,
    surface = StudioSlate,
    onSurface = StudioTextPrimary,
    surfaceVariant = ElevatedPanel,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = ElevatedPanel,
    surfaceContainerHigh = PanelHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = StudioError,
    onError = Color.White,
    errorContainer = StudioErrorContainer,
    onErrorContainer = OnStudioErrorContainer
)

private val FrameCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FrameCraftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FrameCraftDarkColorScheme,
        typography = FrameCraftTypography,
        shapes = FrameCraftShapes,
        content = content
    )
}
