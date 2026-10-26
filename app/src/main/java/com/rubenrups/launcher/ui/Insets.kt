package com.rubenrups.launcher.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Provided from the settings: keep clear of a camera cut-out, and extra space at the top (dp). */
internal val LocalAvoidCutout = compositionLocalOf { true }
internal val LocalExtraTopDp = compositionLocalOf { 0 }

/**
 * The space the launcher keeps free at the screen edges: the system bars, plus (optionally) a punch-hole camera or
 * notch that would otherwise sit on top of the content once the status bar is hidden, plus the user's extra padding.
 */
@Composable
internal fun launcherInsets(): WindowInsets {
    val bars = WindowInsets.systemBars
    val base = if (LocalAvoidCutout.current) bars.union(WindowInsets.displayCutout) else bars
    val extra = LocalExtraTopDp.current
    return if (extra > 0) base.add(WindowInsets(top = extra.dp)) else base
}
