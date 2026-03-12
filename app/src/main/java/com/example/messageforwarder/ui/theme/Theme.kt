package com.example.messageforwarder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SignalMint,
    onPrimary = CarbonNight,
    primaryContainer = DeepCurrent,
    secondary = EmberHighlight,
    background = CarbonNight,
    surface = NightPanel,
    surfaceVariant = DeepCurrent,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCurrent,
    onPrimary = WhiteSand,
    primaryContainer = MistBlue,
    secondary = EmberHighlight,
    onSecondary = CarbonNight,
    background = WhiteSand,
    surface = SoftPaper,
    surfaceVariant = MistBlue,
    onSurface = CarbonNight,
    onSurfaceVariant = SlateInk,
)

@Composable
fun MessageForwarderTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}
