package com.ethred.panorama.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentBlue,
    tertiary = CaptureBlue,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = TextLight,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight,
    error = ErrorRed
)

@Composable
fun Ethred360Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
