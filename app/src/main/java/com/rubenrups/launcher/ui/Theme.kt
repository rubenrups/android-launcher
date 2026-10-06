package com.rubenrups.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LauncherColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Transparent,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    onBackground = Color.White,
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LauncherColors, content = content)
}
