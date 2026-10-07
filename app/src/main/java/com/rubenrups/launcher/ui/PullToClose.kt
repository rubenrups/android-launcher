package com.rubenrups.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * Pull a list down from its top to dismiss what it sits in (the app drawer).
 *
 * This watches the touch itself instead of relying on the list reporting leftover scroll, which
 * depends on the list's own state and overscroll effect and made the gesture flaky. The rule is
 * simple: if a finger lands while the list is already at the top and moves down, we take that drag
 * (the list never sees it); any other gesture is left alone and scrolls the list normally.
 *
 * [onPull] reports how far the finger has pulled (px, past touch slop); [onEnd] says whether the
 * pull was far or fast enough to close, so the caller can finish closing or spring back.
 */
fun Modifier.pullDownToClose(
    listState: LazyListState,
    closeDistance: Dp,
    closeVelocity: Float,
    onPull: (pulledPx: Float) -> Unit,
    onEnd: (close: Boolean) -> Unit,
): Modifier = composed {
    val currentOnPull by rememberUpdatedState(onPull)
    val currentOnEnd by rememberUpdatedState(onEnd)
    pointerInput(listState) {
        val slop = viewConfiguration.touchSlop
        val closePx = closeDistance.toPx()
        awaitEachGesture {
            // Initial pass: we see the touch before the list does, and may take it from it.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            if (!atTop) return@awaitEachGesture

            var totalX = 0f
            var totalY = 0f
            var pulling = false
            var finished = false
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) break // a second finger: not a pull
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (pulling) {
                        change.consume()
                        val far = totalY - slop > closePx
                        val fast = velocity.calculateVelocity().y > closeVelocity
                        currentOnEnd(far || fast)
                        finished = true
                    }
                    break
                }
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!pulling) {
                    when {
                        totalY < -slop -> break // moving up: the list scrolls as usual
                        abs(totalX) > slop * 2 -> break // sideways
                        totalY > slop && totalY > abs(totalX) * 1.5f -> pulling = true
                    }
                }
                if (pulling) {
                    change.consume()
                    currentOnPull((totalY - slop).coerceAtLeast(0f))
                }
            }
            // Interrupted mid-pull (second finger, cancelled): spring back rather than leave it half open.
            if (pulling && !finished) currentOnEnd(false)
        }
    }
}
