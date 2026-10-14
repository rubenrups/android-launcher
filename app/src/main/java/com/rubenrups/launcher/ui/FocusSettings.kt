package com.rubenrups.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.formatMinutes
import java.util.Calendar

private val Days = listOf(
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun",
)

/** The Focus mode settings page: switch it on, set a schedule, and choose which apps get hidden. */
@Composable
internal fun FocusPage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val chosen = installed.filter { it.key in settings.focusApps }.sortedBy { it.label.lowercase() }

    Group("Apps to hide") {
        chosen.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "Remove",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { viewModel.update { it.copy(focusApps = it.focusApps - app.key) } }.padding(12.dp),
                )
            }
        }
        NavRow("Choose apps") { picking = true }
        Hint("While focus mode is on, these apps disappear from the home screen, the drawer and search. Nothing is uninstalled or changed.")
    }
    Group("Turn it on") {
        SwitchRow(
            "Focus mode",
            settings.focusManual,
            subtitle = "On until you turn it off (or pause it from the home screen)",
        ) { on -> viewModel.setFocusManual(on) }
        SwitchRow("Turn on by schedule", settings.focusScheduleOn) { on -> viewModel.update { it.copy(focusScheduleOn = on) } }
        if (settings.focusScheduleOn) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Days.forEach { (day, label) ->
                    val on = day in settings.focusDays
                    Text(
                        label,
                        color = if (on) Color(0xFF14181B) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .clickable { viewModel.update { it.copy(focusDays = if (on) it.focusDays - day else it.focusDays + day) } }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            SliderRow("From", formatMinutes(settings.focusStartMin), settings.focusStartMin / 30f, 0f..47f) { v ->
                viewModel.update { it.copy(focusStartMin = v.toInt() * 30) }
            }
            SliderRow("Until", formatMinutes(settings.focusEndMin), settings.focusEndMin / 30f, 0f..47f) { v ->
                viewModel.update { it.copy(focusEndMin = v.toInt() * 30) }
            }
            Hint("A schedule that ends before it starts (22:00 until 06:00) runs overnight.")
        }
    }

    if (picking) {
        Dialog(onDismissRequest = { picking = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
                var query by remember { mutableStateOf("") }
                val listState = rememberLazyListState()
                val shown = installed.filter { matchesQuery(query, it.label) }
                Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp)) {
                    Text(
                        "Apps to hide in focus mode",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    PickerSearchField(query) { query = it }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                        items(shown, key = { it.key }) { app ->
                            val inFocus = app.key in settings.focusApps
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.update { s -> s.copy(focusApps = if (inFocus) s.focusApps - app.key else s.focusApps + app.key) }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AppIcon(app, viewModel, size = 32.dp)
                                Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.weight(1f))
                                Checkbox(checked = inFocus, onCheckedChange = null)
                            }
                        }
                    }
                    TextButton(onClick = { picking = false }, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
                }
            }
        }
    }
}
