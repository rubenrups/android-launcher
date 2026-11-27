package com.rubenrups.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.MindfulPrompt
import kotlinx.coroutines.delay

/**
 * The pause before an app opens: its icon breathes while a countdown runs, then "Open anyway" becomes possible. When a
 * daily limit is reached it says so, and with a strict rule there is nothing to open. "Not now" (or back) closes it.
 */
@Composable
fun MindfulScreen(viewModel: LauncherViewModel, prompt: MindfulPrompt) {
    val rule = prompt.rule
    val reached = rule.limitReached(prompt.opensToday)
    var remaining by remember(prompt) { mutableIntStateOf(rule.pauseSeconds) }
    LaunchedEffect(prompt) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    BackHandler { viewModel.dismissMindful() }
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "breatheScale",
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2101417))
            // Swallow touches so nothing underneath reacts while the pause is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(launcherInsets())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.scale(breathe)) { AppIcon(prompt.app, viewModel, 96.dp) }
        Spacer(Modifier.height(24.dp))
        Text(prompt.app.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                reached -> "You have opened it ${prompt.opensToday} times today. Your limit is ${rule.dailyLimit}."
                prompt.opensToday > 0 -> "Take a breath. You have opened it ${prompt.opensToday} times today."
                else -> "Take a breath. Do you want to open it?"
            },
            color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "Not now",
            color = Color(0xFF14181B), fontSize = 18.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(50))
                .clickable { viewModel.dismissMindful() }
                .padding(horizontal = 36.dp, vertical = 14.dp),
        )
        if (rule.canOpen(prompt.opensToday)) {
            Spacer(Modifier.height(16.dp))
            val ready = remaining <= 0
            Text(
                if (ready) "Open anyway" else "Open in $remaining",
                color = Color.White.copy(alpha = if (ready) 0.85f else 0.35f), fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = ready) { viewModel.confirmMindful() }.padding(12.dp),
            )
        }
    }
}
