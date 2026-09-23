package com.example.plane_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = PanelDark,
    secondary = SignalGreen,
    background = PanelDark,
    onBackground = TextBright,
    surface = PanelDarkLight,
    onSurface = TextBright,
    surfaceVariant = PanelDarkLight,
    onSurfaceVariant = TextMuted
)

/** Forces the dark FR24-style theme regardless of system setting. */
@Composable
fun PlaneTrackerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography,
        content = content
    )
}
