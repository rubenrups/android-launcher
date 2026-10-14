package com.rubenrups.launcher.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/** The highlight colour used for links, ticks and small buttons. Set by [LauncherTheme] from the settings. */
val LocalAccent = compositionLocalOf { Color(0xFF8AB4F8) }

class AccentOption(val key: String, val label: String, val color: Color)

val AccentOptions = listOf(
    AccentOption("blue", "Blue", Color(0xFF8AB4F8)),
    AccentOption("green", "Green", Color(0xFF7BE0A0)),
    AccentOption("purple", "Purple", Color(0xFFC4A1FF)),
    AccentOption("orange", "Orange", Color(0xFFFFB74D)),
    AccentOption("pink", "Pink", Color(0xFFFF8FB1)),
    AccentOption("teal", "Teal", Color(0xFF5ED8D0)),
    AccentOption("red", "Red", Color(0xFFFF6B6B)),
    AccentOption("white", "White", Color.White),
)

/** "Match my wallpaper" (Android 12+): the accent follows the system's wallpaper colours. */
const val ACCENT_WALLPAPER = "wallpaper"

class FontOption(val key: String, val label: String, val family: FontFamily)

val FontOptions = listOf(
    FontOption("default", "System default", FontFamily.Default),
    FontOption("sans", "Sans serif", FontFamily.SansSerif),
    FontOption("light", "Light", FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))),
    FontOption("condensed", "Condensed", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    FontOption("serif", "Serif", FontFamily.Serif),
    FontOption("mono", "Monospace", FontFamily.Monospace),
    FontOption("cursive", "Cursive", FontFamily.Cursive),
)

/** The accent colour as a composable value, so `color = Accent` follows the chosen theme. */
internal val Accent: androidx.compose.ui.graphics.Color
    @Composable get() = LocalAccent.current

private val LauncherColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Transparent,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    onBackground = Color.White,
)

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun LauncherTheme(accentKey: String = "blue", fontKey: String = "default", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accent = if (accentKey == ACCENT_WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).primary
    } else {
        AccentOptions.firstOrNull { it.key == accentKey }?.color ?: AccentOptions.first().color
    }
    val family = FontOptions.firstOrNull { it.key == fontKey }?.family ?: FontFamily.Default
    val typography = remember(family) { Typography().withFont(family) }
    MaterialTheme(colorScheme = LauncherColors.copy(primary = accent), typography = typography) {
        CompositionLocalProvider(LocalAccent provides accent, content = content)
    }
}
