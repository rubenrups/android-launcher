package com.rubenrups.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Counts quick taps (and a hold) on space that no child used. [maxTaps] is how many taps anything is set for:
 * when it is 1 a tap is reported at once, otherwise a tap waits briefly to see whether more follow, so a double
 * tap is only delayed when a triple tap is also in use. [onTaps] gets the number of taps and where the last one was.
 */
fun Modifier.multiTap(
    enabled: Boolean,
    maxTaps: () -> Int,
    onLongPress: (Offset) -> Unit,
    onTaps: (Int, Offset) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        var current = awaitFirstDown()
        var taps = 0
        while (true) {
            var up: PointerInputChange? = null
            var held = false
            try {
                up = withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            } catch (_: PointerEventTimeoutCancellationException) {
                held = true
            }
            if (held) {
                if (taps == 0) {
                    onLongPress(current.position)
                    waitForUpOrCancellation()?.consume()
                }
                return@awaitEachGesture
            }
            if (up == null) return@awaitEachGesture
            up.consume()
            taps++
            if (taps >= maxTaps()) {
                onTaps(taps, up.position)
                return@awaitEachGesture
            }
            val next = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
            if (next == null) {
                onTaps(taps, up.position)
                return@awaitEachGesture
            }
            current = next
        }
    }
}
