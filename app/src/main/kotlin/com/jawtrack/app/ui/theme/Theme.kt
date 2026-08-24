package com.jawtrack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark-first palette: this app is used at the bedside, at night, with the screen dimmed.
private val JawTrackDarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7FE0C1),
    secondary = androidx.compose.ui.graphics.Color(0xFFB7C4D8),
    background = androidx.compose.ui.graphics.Color(0xFF10151C),
    surface = androidx.compose.ui.graphics.Color(0xFF1B2430),
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
)

@Composable
fun JawTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JawTrackDarkColors,
        content = content
    )
}
