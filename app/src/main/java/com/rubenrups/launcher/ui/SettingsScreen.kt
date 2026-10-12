package com.rubenrups.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.CalendarReader
import com.rubenrups.launcher.data.SwipeAction
import com.rubenrups.launcher.data.UpdateChecker
import com.rubenrups.launcher.data.WebSearchEngine
import com.rubenrups.launcher.notifications.NotificationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val visibleApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf<String?>(null) } // "left" or "right" while the app chooser is open
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.backupJson().toByteArray()) }
            }.isSuccess
            backupStatus = if (saved) "Backup saved." else "Could not save the backup."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            backupStatus = if (text != null && viewModel.restoreBackup(text)) {
                "Backup restored."
            } else {
                "That file is not a Rups Launcher backup."
            }
        }
    }
    fun swipeLabel(key: String) = when (key) {
        LauncherViewModel.ACTION_DASHBOARD -> "Dashboard"
        else -> visibleApps.firstOrNull { it.key == key }?.label ?: "Nothing"
    }
    var pickWidget by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val installedBuild = remember { UpdateChecker.installedBuild(context) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(showCalendar = granted) }
    }
    val userManager = remember { context.getSystemService(UserManager::class.java) }
    var profileVersion by remember { mutableIntStateOf(0) }
    val otherProfiles = remember(profileVersion) { userManager.userProfiles.filter { it != Process.myUserHandle() } }
    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
    // Queried once per open; installing a pack while settings are open needs a reopen.
    val iconPacks = remember { viewModel.installedIconPacks() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScrollbar(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Text("Done", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }

        Section("Layout")
        ChoiceRow("Left-aligned, with icons", selected = !settings.alignEnd && settings.showIcons) {
            viewModel.update { it.copy(alignEnd = false, anchorBottom = false, showIcons = true, favouriteTextSp = 22) }
        }
        ChoiceRow("Right-aligned, large text", selected = settings.alignEnd && !settings.showIcons) {
            viewModel.update { it.copy(alignEnd = true, anchorBottom = true, showIcons = false, favouriteTextSp = 34) }
        }

        Section("Home screen")
        SwitchRow("Show clock", settings.showClock) { v -> viewModel.update { it.copy(showClock = v) } }
        SwitchRow("Show notifications", settings.showNotifications) { v -> viewModel.update { it.copy(showNotifications = v) } }
        SwitchRow("Show media player", settings.showMedia) { v -> viewModel.update { it.copy(showMedia = v) } }
        SwitchRow("Show next calendar event", settings.showCalendar) { v ->
            if (v && !CalendarReader.hasPermission(context)) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            } else {
                viewModel.update { it.copy(showCalendar = v) }
            }
        }
        SwitchRow("Show weather", settings.showWeather) { v -> viewModel.update { it.copy(showWeather = v) } }
        if (settings.showWeather) {
            TextField(
                value = settings.weatherCity,
                onValueChange = { v -> viewModel.update { it.copy(weatherCity = v) } },
                singleLine = true,
                placeholder = { Text("City, e.g. Amsterdam") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            SwitchRow("Use °F instead of °C", settings.weatherFahrenheit) { v ->
                viewModel.update { it.copy(weatherFahrenheit = v) }
            }
            Hint("Weather is fetched from open-meteo.com using only the city name.")
        }
        SwitchRow("Open folders as a popup (off: expand in place)", settings.folderPopupOnHome) { v ->
            viewModel.update { it.copy(folderPopupOnHome = v) }
        }
        SwitchRow("Two columns in landscape", settings.twoColumnsLandscape) { v ->
            viewModel.update { it.copy(twoColumnsLandscape = v) }
        }
        SwitchRow("Adapt text colour to the wallpaper", settings.adaptiveText) { v ->
            viewModel.update { it.copy(adaptiveText = v) }
        }
        SwitchRow("Show app icons", settings.showIcons) { v -> viewModel.update { it.copy(showIcons = v) } }
        SwitchRow("Show search button", settings.showSearchButton) { v -> viewModel.update { it.copy(showSearchButton = v) } }
        SwitchRow("Align to the right", settings.alignEnd) { v -> viewModel.update { it.copy(alignEnd = v) } }
        SwitchRow("Pin list to the bottom", settings.anchorBottom) { v -> viewModel.update { it.copy(anchorBottom = v) } }
        Label("Favourites text size: ${settings.favouriteTextSp}sp")
        Slider(
            value = settings.favouriteTextSp.toFloat(),
            onValueChange = { v -> viewModel.update { it.copy(favouriteTextSp = v.toInt()) } },
            valueRange = 24f..48f,
        )

        Section("Notifications & media")
        Text(
            if (listenerConnected) "Notification access: connected ✓" else "Notification access: not connected",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        ActionRow("Open notification access settings") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        ActionRow("Open app info") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Hint(
            "The media player and notification list need notification access. If the switch is greyed out " +
                "(common for apps installed from a file), open App info → ⋮ menu → Allow restricted settings, then try again."
        )

        Section("Drawer & search")
        SwitchRow("Show recent apps", settings.showRecents) { v -> viewModel.update { it.copy(showRecents = v) } }
        Label("Web search when typing in the drawer")
        WebSearchEngine.entries.forEach { engine ->
            ChoiceRow(engine.label, selected = settings.webSearch == engine) {
                viewModel.update { it.copy(webSearch = engine) }
            }
        }

        Section("Widgets")
        settings.widgetIds.forEach { id ->
            val label = viewModel.widgets.info(id)?.loadLabel(context.packageManager)?.toString() ?: "Unavailable widget"
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Remove", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { viewModel.removeWidget(id) }.padding(8.dp))
            }
        }
        ActionRow("Add widget") { pickWidget = true }
        if (pickWidget) WidgetPickerDialog(viewModel, onDismiss = { pickWidget = false })

        if (otherProfiles.isNotEmpty()) {
            Section("Work profile")
            otherProfiles.forEach { user ->
                val paused = remember(profileVersion, user) { userManager.isQuietModeEnabled(user) }
                Text(
                    if (paused) "Work apps are paused" else "Work apps are active",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                ActionRow(if (paused) "Resume work apps" else "Pause work apps") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        runCatching { userManager.requestQuietModeEnabled(!paused, user) }
                    }
                    profileVersion++
                }
            }
            Hint("Work apps appear in the drawer with a 💼 and a badge on their icon.")
        }

        Section("Dashboard & glucose")
        Hint("Open the dashboard from the long-press menu, by tapping the glucose line, or by choosing it under Swipe left / right below.")
        SwitchRow("Show navigation on home", settings.showNavigation) { v -> viewModel.update { it.copy(showNavigation = v) } }
        Hint(
            "While Google Maps or Waze is navigating, its next-turn notification is shown here. " +
                "Needs notification access (see Notifications & media)."
        )
        SwitchRow("Show glucose (LibreLinkUp)", settings.glucoseEnabled) { v -> viewModel.update { it.copy(glucoseEnabled = v) } }
        if (settings.glucoseEnabled) {
            TextField(
                value = settings.glucoseEmail,
                onValueChange = { v -> viewModel.update { it.copy(glucoseEmail = v) } },
                singleLine = true,
                placeholder = { Text("LibreLinkUp email") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            TextField(
                value = settings.glucosePassword,
                onValueChange = { v -> viewModel.update { it.copy(glucosePassword = v) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("LibreLinkUp password") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            ActionRow("Tapping the value opens: ${settings.glucoseApp.let { k -> if (k.isBlank()) "LibreLink (automatic)" else swipeLabel(k) }}") {
                chooser = "glucose"
            }
            SwitchRow("Use mmol/L instead of mg/dL", settings.glucoseMmol) { v -> viewModel.update { it.copy(glucoseMmol = v) } }
            Label("Low below ${formatGlucose(settings.glucoseLow, settings.glucoseMmol)} ${glucoseUnit(settings.glucoseMmol)}")
            Slider(
                value = settings.glucoseLow.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(glucoseLow = v.toInt()) } },
                valueRange = 50f..110f,
            )
            Label("High above ${formatGlucose(settings.glucoseHigh, settings.glucoseMmol)} ${glucoseUnit(settings.glucoseMmol)}")
            Slider(
                value = settings.glucoseHigh.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(glucoseHigh = v.toInt()) } },
                valueRange = 120f..300f,
            )
            Hint(
                "Use the login of a LibreLinkUp follower account that your Libre sensor is shared with " +
                    "(not the LibreLink account itself). It is stored on this device only and never included in backups. " +
                    "This uses an unofficial service that can change without notice. A reading older than 10 minutes is greyed out. " +
                    "For display only: never use it for treatment decisions."
            )
        }

        Section("Backup")
        ActionRow("Save a backup file") { exportLauncher.launch("rups-launcher-backup.json") }
        ActionRow("Restore from a backup file") { importLauncher.launch(arrayOf("*/*")) }
        backupStatus?.let { Hint(it) }
        Hint("Favourites, folders, hidden apps and settings. The GitHub token is never included.")

        Section("Updates")
        Text("Installed: build $installedBuild", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        ActionRow("Check for updates") {
            updateStatus = "Checking…"
            updateUrl = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { UpdateChecker.check(context, settings.updateToken) }
                when (result) {
                    is UpdateChecker.Result.UpToDate -> updateStatus = "You are up to date (build ${result.installed})."
                    is UpdateChecker.Result.Available -> {
                        updateStatus = "Build ${result.update.build} is available."
                        updateUrl = result.update.pageUrl
                    }
                    is UpdateChecker.Result.Failed -> updateStatus = result.reason
                }
            }
        }
        updateStatus?.let { Hint(it) }
        updateUrl?.let { url ->
            ActionRow("Open download page") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Label("GitHub token (optional)")
        TextField(
            value = settings.updateToken,
            onValueChange = { v -> viewModel.update { it.copy(updateToken = v) } },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("Read-only token") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        Hint(
            "Only needed while the repository is private. Create a fine-grained token with read-only " +
                "access to this repository's contents. It is stored on this device only."
        )

        Section("Swipe down on home")
        SwipeAction.entries.forEach { action ->
            ChoiceRow(action.label, selected = settings.swipeDown == action) {
                viewModel.update { it.copy(swipeDown = action) }
            }
        }

        Section("Swipe left / right on home")
        ActionRow("Swipe left opens: ${swipeLabel(settings.swipeLeftApp)}") { chooser = "left" }
        ActionRow("Swipe right opens: ${swipeLabel(settings.swipeRightApp)}") { chooser = "right" }
        chooser?.let { side ->
            val forGlucose = side == "glucose"
            AppChooserDialog(
                title = when (side) {
                    "left" -> "Swipe left opens"
                    "right" -> "Swipe right opens"
                    else -> "Tapping the glucose value opens"
                },
                viewModel = viewModel,
                onPick = { app ->
                    val key = app?.key.orEmpty()
                    viewModel.update {
                        when (side) {
                            "left" -> it.copy(swipeLeftApp = key)
                            "right" -> it.copy(swipeRightApp = key)
                            else -> it.copy(glucoseApp = key)
                        }
                    }
                    chooser = null
                },
                onDismiss = { chooser = null },
                extras = if (forGlucose) emptyList() else listOf("Dashboard" to LauncherViewModel.ACTION_DASHBOARD),
                onPickExtra = { key ->
                    viewModel.update { if (side == "left") it.copy(swipeLeftApp = key) else it.copy(swipeRightApp = key) }
                    chooser = null
                },
            )
        }

        Section("Icon pack")
        ChoiceRow("System default", selected = settings.iconPack == null) {
            viewModel.update { it.copy(iconPack = null) }
        }
        iconPacks.forEach { pack ->
            ChoiceRow(pack.label, selected = settings.iconPack == pack.packageName) {
                viewModel.update { it.copy(iconPack = pack.packageName) }
            }
        }
        if (iconPacks.isEmpty()) Hint("No ADW/Nova-compatible icon packs installed.")

        Section("Hidden apps")
        if (hidden.isEmpty()) Hint("Long-press an app in the drawer and choose \"Hide app\".")
        hidden.forEach { app ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Unhide", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { viewModel.unhide(app) }.padding(8.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Label(text: String) = Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun Hint(text: String) = Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Color.White, fontSize = 18.sp)
    }
}
