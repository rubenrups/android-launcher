package com.rubenrups.launcher.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo

/** Pick one app (or "Nothing"). Used for the swipe-left / swipe-right shortcuts. */
@Composable
fun AppChooserDialog(
    title: String,
    viewModel: LauncherViewModel,
    onPick: (AppInfo?) -> Unit,
    onDismiss: () -> Unit,
    /** Extra non-app choices (label to key), such as the dashboard, listed above the apps. */
    extras: List<Pair<String, String>> = emptyList(),
    onPickExtra: (String) -> Unit = {},
) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xF21A1A1A)) {
            Column(Modifier.heightIn(max = 560.dp).padding(top = 16.dp)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).verticalScrollbar(listState)) {
                    item(key = "nothing") {
                        Text(
                            "Nothing",
                            color = Color.White,
                            fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                    items(extras, key = { it.second }) { (label, key) ->
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPickExtra(key) }.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                    items(apps, key = { it.key }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app) }.padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppIcon(app, viewModel, size = 32.dp)
                            Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Cancel") }
            }
        }
    }
}
