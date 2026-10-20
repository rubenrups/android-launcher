package com.rubenrups.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Tap and long-press with a clear "I felt that": the row shrinks and dims while held, and a long-press
 * gives a haptic tick. Replaces the default ripple, which looks odd on text sitting on a wallpaper.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed) 0.75f else 1f
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick?.let { action ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                }
            },
        )
}
