package com.rubenrups.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.MindfulRule
import kotlin.math.roundToInt

private fun summary(rule: MindfulRule): String =
    "${rule.pauseSeconds} s" + if (rule.dailyLimit > 0) " · limit ${rule.dailyLimit}${if (rule.strict) " (strict)" else ""}" else ""

/** Apps that get a pause screen before they open, each with its own wait and optional daily limit. */
@Composable
internal fun MindfulGroup(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppInfo?>(null) }
    val configured = installed.filter { it.key in settings.mindful }.sortedBy { it.label.lowercase() }

    Group("Mindful launch") {
        configured.forEach { app ->
            NavRow(app.label, summary(settings.mindful.getValue(app.key))) { editing = app }
        }
        NavRow("Add an app") { adding = true }
        Hint(
            "Before one of these apps opens, a pause screen asks if you really want to, and \\"Open anyway\\" only works after " +
                "the wait. A daily limit says when you are over it; with strict on, the app stays closed for the rest of the day. " +
                "Counts only cover opens from this launcher, since this version began recording days."
        )
    }

    if (adding) {
        AppChooserDialog(
            title = "Pause before opening",
            viewModel = viewModel,
            onPick = { app ->
                adding = false
                if (app != null) {
                    viewModel.setMindfulRule(app, settings.mindful[app.key] ?: MindfulRule())
                    editing = app
                }
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { app ->
        MindfulRuleDialog(
            app = app,
            initial = settings.mindful[app.key] ?: MindfulRule(),
            onSave = { viewModel.setMindfulRule(app, it); editing = null },
            onRemove = { viewModel.setMindfulRule(app, null); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MindfulRuleDialog(app: AppInfo, initial: MindfulRule, onSave: (MindfulRule) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var pause by remember { mutableFloatStateOf(initial.pauseSeconds.toFloat()) }
    var limit by remember { mutableFloatStateOf(initial.dailyLimit.toFloat()) }
    var strict by remember { mutableStateOf(initial.strict) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text("Wait: ${pause.roundToInt()} seconds", color = Color.White, fontSize = 15.sp)
                Slider(value = pause, onValueChange = { pause = it }, valueRange = 0f..30f)
                Text(
                    if (limit.roundToInt() == 0) "Daily limit: none" else "Daily limit: ${limit.roundToInt()} opens",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp),
                )
                Slider(value = limit, onValueChange = { limit = it }, valueRange = 0f..20f)
                if (limit.roundToInt() > 0) {
                    Column {
                        Text("Strict: closed once the limit is reached", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Switch(checked = strict, onCheckedChange = { strict = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(MindfulRule(pause.roundToInt(), limit.roundToInt(), strict && limit.roundToInt() > 0)) }) { Text("Save") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
