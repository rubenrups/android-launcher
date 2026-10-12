package com.rubenrups.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.CalendarReader
import com.rubenrups.launcher.data.GestureActions
import com.rubenrups.launcher.data.LauncherSettings
import com.rubenrups.launcher.data.UpdateChecker
import com.rubenrups.launcher.data.UpdateState
import com.rubenrups.launcher.data.VirtualApps
import com.rubenrups.launcher.data.WebSearchEngine
import com.rubenrups.launcher.notifications.NotificationStore

/** The settings categories, in the order they appear on the front page. */
private enum class Page(val title: String, val icon: String) {
    HOME("Home screen", "🏠"),
    APPEARANCE("Appearance", "🎨"),
    GESTURES("Gestures", "👆"),
    DRAWER("App drawer", "🗂️"),
    DASHBOARD("Dashboard & glucose", "📊"),
    NOTIFICATIONS("Notifications & media", "🔔"),
    APPS("Apps & widgets", "🧩"),
    BACKUP("Backup & updates", "☁️"),
}

private val Accent = Color(0xFF8AB4F8)
private val CardColor = Color.White.copy(alpha = 0.07f)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val visibleApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installedBuild = remember { UpdateChecker.installedBuild(context) }

    // Which category is open ("" = the front page). Saved, so rotating the phone keeps your place.
    var pageName by rememberSaveable { mutableStateOf("") }
    val page = Page.entries.firstOrNull { it.name == pageName }
    var chooser by remember { mutableStateOf<String?>(null) } // up / down / left / right / glucose while the app chooser is open
    var pickWidget by remember { mutableStateOf(false) }

    BackHandler(enabled = page != null) { pageName = "" }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF00B0D10))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Crossfade(targetState = page, label = "settingsPage") { current ->
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScrollbar(scroll)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (current == null) {
                    Header("Settings", actionLabel = "Done", onAction = onClose)
                    Spacer(Modifier.height(8.dp))
                    Page.entries.forEach { entry ->
                        val summary = when (entry) {
                            Page.HOME -> "Clock, weather, favourites"
                            Page.APPEARANCE -> "Text, icons, wallpaper colours"
                            Page.GESTURES -> "Swipe up, down, left, right"
                            Page.DRAWER -> "Search, recents, background"
                            Page.DASHBOARD -> if (settings.glucoseEnabled) "Glucose is on" else "Glucose, buttons, background"
                            Page.NOTIFICATIONS -> if (listenerConnected) "Access connected ✓" else "Access not connected"
                            Page.APPS -> "Widgets, hidden apps, work profile"
                            Page.BACKUP -> when (updateState) {
                                is UpdateState.Ready, is UpdateState.Available -> "Update available"
                                else -> "Backups, auto-update"
                            }
                        }
                        CategoryRow(entry.icon, entry.title, summary) { pageName = entry.name }
                    }
                    Text(
                        "Rups Launcher · build $installedBuild",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp).align(Alignment.CenterHorizontally),
                    )
                } else {
                    Header(current.title, backLabel = "‹", onBack = { pageName = "" })
                    when (current) {
                        Page.HOME -> HomePage(viewModel, settings)
                        Page.APPEARANCE -> AppearancePage(viewModel, settings)
                        Page.GESTURES -> GesturesPage(settings, visibleApps) { chooser = it }
                        Page.DRAWER -> DrawerPage(viewModel, settings)
                        Page.DASHBOARD -> DashboardPage(viewModel, settings, visibleApps) { chooser = it }
                        Page.NOTIFICATIONS -> NotificationsPage(viewModel, settings, listenerConnected)
                        Page.APPS -> AppsPage(viewModel, settings) { pickWidget = true }
                        Page.BACKUP -> BackupPage(viewModel, settings, updateState)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (pickWidget) WidgetPickerDialog(viewModel, onDismiss = { pickWidget = false })

    chooser?.let { side ->
        fun set(key: String) {
            viewModel.update {
                when (side) {
                    "up" -> it.copy(swipeUp = key)
                    "down" -> it.copy(swipeDown = key)
                    "left" -> it.copy(swipeLeftApp = key)
                    "right" -> it.copy(swipeRightApp = key)
                    else -> it.copy(glucoseApp = key)
                }
            }
            chooser = null
        }
        AppChooserDialog(
            title = when (side) {
                "up" -> "Swipe up"
                "down" -> "Swipe down"
                "left" -> "Swipe left"
                "right" -> "Swipe right"
                else -> "Tapping the glucose value opens"
            },
            viewModel = viewModel,
            onPick = { app -> set(app?.key.orEmpty()) },
            onDismiss = { chooser = null },
            extras = if (side == "glucose") emptyList() else GestureActions.builtIn.map { (key, label) -> label to key },
            onPickExtra = { key -> set(key) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomePage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val context = LocalContext.current
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(showCalendar = granted) }
    }
    Group("Header") {
        SwitchRow("Clock", settings.showClock) { v -> viewModel.update { it.copy(showClock = v) } }
        SwitchRow("Next calendar event", settings.showCalendar) { v ->
            if (v && !CalendarReader.hasPermission(context)) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            } else {
                viewModel.update { it.copy(showCalendar = v) }
            }
        }
        SwitchRow("Weather", settings.showWeather) { v -> viewModel.update { it.copy(showWeather = v) } }
        if (settings.showWeather) {
            Field(settings.weatherCity, "City, e.g. Amsterdam") { v -> viewModel.update { it.copy(weatherCity = v) } }
            SwitchRow("Use °F instead of °C", settings.weatherFahrenheit) { v ->
                viewModel.update { it.copy(weatherFahrenheit = v) }
            }
            Hint("Weather comes from open-meteo.com and only the city name is sent.")
        }
    }
    Group("Favourites list") {
        SwitchRow("Align to the right", settings.alignEnd) { v -> viewModel.update { it.copy(alignEnd = v) } }
        SwitchRow("Pin list to the bottom", settings.anchorBottom) { v -> viewModel.update { it.copy(anchorBottom = v) } }
        SwitchRow("Two columns in landscape", settings.twoColumnsLandscape) { v ->
            viewModel.update { it.copy(twoColumnsLandscape = v) }
        }
        SwitchRow(
            "Open folders as a popup",
            settings.folderPopupOnHome,
            subtitle = "Off: folders expand in place",
        ) { v -> viewModel.update { it.copy(folderPopupOnHome = v) } }
    }
    Group("Buttons") {
        SwitchRow("Search button", settings.showSearchButton) { v -> viewModel.update { it.copy(showSearchButton = v) } }
        VirtualApps.all.forEach { button ->
            SwitchRow(
                "${button.label} button in favourites",
                button.key in settings.favourites,
            ) { on -> if (on) viewModel.addFavourite(button) else viewModel.removeFavourite(button) }
        }
    }
}

@Composable
private fun AppearancePage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val iconPacks = remember { viewModel.installedIconPacks() }
    Group("Presets") {
        ChoiceRow("Left-aligned, with icons", selected = !settings.alignEnd && settings.showIcons) {
            viewModel.update { it.copy(alignEnd = false, anchorBottom = false, showIcons = true, favouriteTextSp = 22) }
        }
        ChoiceRow("Right-aligned, large text", selected = settings.alignEnd && !settings.showIcons) {
            viewModel.update { it.copy(alignEnd = true, anchorBottom = true, showIcons = false, favouriteTextSp = 34) }
        }
    }
    Group("Favourites") {
        SwitchRow("Show app icons", settings.showIcons) { v -> viewModel.update { it.copy(showIcons = v) } }
        SliderRow(
            "Text size",
            "${settings.favouriteTextSp} sp",
            settings.favouriteTextSp.toFloat(),
            24f..48f,
        ) { v -> viewModel.update { it.copy(favouriteTextSp = v.toInt()) } }
        SwitchRow(
            "Adapt text colour to the wallpaper",
            settings.adaptiveText,
            subtitle = "Dark text on light wallpapers",
        ) { v -> viewModel.update { it.copy(adaptiveText = v) } }
    }
    Group("Icon pack") {
        ChoiceRow("System default", selected = settings.iconPack == null) {
            viewModel.update { it.copy(iconPack = null) }
        }
        iconPacks.forEach { pack ->
            ChoiceRow(pack.label, selected = settings.iconPack == pack.packageName) {
                viewModel.update { it.copy(iconPack = pack.packageName) }
            }
        }
        if (iconPacks.isEmpty()) Hint("No ADW/Nova-compatible icon packs are installed.")
    }
}

@Composable
private fun GesturesPage(settings: LauncherSettings, apps: List<AppInfo>, openChooser: (String) -> Unit) {
    Group("Swipes on the home screen") {
        NavRow("Swipe up", labelFor(settings.swipeUp, apps)) { openChooser("up") }
        NavRow("Swipe down", labelFor(settings.swipeDown, apps)) { openChooser("down") }
        NavRow("Swipe left", labelFor(settings.swipeLeftApp, apps)) { openChooser("left") }
        NavRow("Swipe right", labelFor(settings.swipeRightApp, apps)) { openChooser("right") }
        Hint(
            "Each swipe can open the drawer, search, the notification shade, the dashboard or any app. " +
                "A short label shows what it will do while you swipe."
        )
    }
}

@Composable
private fun DrawerPage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    Group("Look") {
        SwitchRow(
            "Attach below the home screen",
            settings.drawerAttached,
            subtitle = "One tall page that slides together. Off: the drawer fades in over home.",
        ) { v -> viewModel.update { it.copy(drawerAttached = v) } }
        SliderRow(
            "Background",
            "${settings.drawerOpacity}% solid",
            settings.drawerOpacity.toFloat(),
            0f..100f,
        ) { v -> viewModel.update { it.copy(drawerOpacity = v.toInt()) } }
    }
    Group("Content") {
        SwitchRow("Recent apps", settings.showRecents) { v -> viewModel.update { it.copy(showRecents = v) } }
    }
    Group("Web search when typing") {
        WebSearchEngine.entries.forEach { engine ->
            ChoiceRow(engine.label, selected = settings.webSearch == engine) {
                viewModel.update { it.copy(webSearch = engine) }
            }
        }
    }
}

@Composable
private fun DashboardPage(
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    apps: List<AppInfo>,
    openChooser: (String) -> Unit,
) {
    Group("Dashboard") {
        SliderRow(
            "Background",
            "${settings.dashboardOpacity}% solid",
            settings.dashboardOpacity.toFloat(),
            0f..100f,
        ) { v -> viewModel.update { it.copy(dashboardOpacity = v.toInt()) } }
        Hint("Open it from the long-press menu, a swipe (see Gestures), or a favourites button (see Home screen).")
    }
    Group("Glucose (LibreLinkUp)") {
        SwitchRow("Show glucose", settings.glucoseEnabled) { v -> viewModel.update { it.copy(glucoseEnabled = v) } }
        if (settings.glucoseEnabled) {
            Field(settings.glucoseEmail, "LibreLinkUp email") { v -> viewModel.update { it.copy(glucoseEmail = v) } }
            Field(settings.glucosePassword, "LibreLinkUp password", password = true) { v ->
                viewModel.update { it.copy(glucosePassword = v) }
            }
            NavRow(
                "Tapping the value opens",
                if (settings.glucoseApp.isBlank()) "LibreLink (automatic)" else labelFor(settings.glucoseApp, apps),
            ) { openChooser("glucose") }
            SwitchRow("Use mmol/L instead of mg/dL", settings.glucoseMmol) { v -> viewModel.update { it.copy(glucoseMmol = v) } }
            SliderRow(
                "Low below",
                "${formatGlucose(settings.glucoseLow, settings.glucoseMmol)} ${glucoseUnit(settings.glucoseMmol)}",
                settings.glucoseLow.toFloat(),
                50f..110f,
            ) { v -> viewModel.update { it.copy(glucoseLow = v.toInt()) } }
            SliderRow(
                "High above",
                "${formatGlucose(settings.glucoseHigh, settings.glucoseMmol)} ${glucoseUnit(settings.glucoseMmol)}",
                settings.glucoseHigh.toFloat(),
                120f..300f,
            ) { v -> viewModel.update { it.copy(glucoseHigh = v.toInt()) } }
        }
        Hint(
            "Use the login of a LibreLinkUp follower account your sensor is shared with (not the LibreLink account " +
                "itself). It stays on this device and is never part of a backup. This uses an unofficial service that " +
                "can change without notice. A reading older than 10 minutes is greyed out. Display only: never use it " +
                "for treatment decisions."
        )
    }
}

@Composable
private fun NotificationsPage(viewModel: LauncherViewModel, settings: LauncherSettings, listenerConnected: Boolean) {
    val context = LocalContext.current
    Group("Access") {
        InfoRow("Notification access", if (listenerConnected) "Connected ✓" else "Not connected")
        NavRow("Open notification access settings") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        NavRow("Open app info") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Hint(
            "The media player, notification list and navigation card need notification access. If the switch is " +
                "greyed out (common for apps installed from a file), open App info → ⋮ menu → Allow restricted " +
                "settings, then try again."
        )
    }
    Group("Show on the home screen") {
        SwitchRow("Notifications", settings.showNotifications) { v -> viewModel.update { it.copy(showNotifications = v) } }
        SwitchRow("Media player", settings.showMedia) { v -> viewModel.update { it.copy(showMedia = v) } }
        SwitchRow(
            "Navigation",
            settings.showNavigation,
            subtitle = "Next turn from Google Maps or Waze while navigating",
        ) { v -> viewModel.update { it.copy(showNavigation = v) } }
    }
}

@Composable
private fun AppsPage(viewModel: LauncherViewModel, settings: LauncherSettings, onAddWidget: () -> Unit) {
    val context = LocalContext.current
    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val userManager = remember { context.getSystemService(UserManager::class.java) }
    var profileVersion by remember { mutableIntStateOf(0) }
    val otherProfiles = remember(profileVersion) { userManager.userProfiles.filter { it != Process.myUserHandle() } }

    Group("Widgets") {
        settings.widgetIds.forEach { id ->
            val label = viewModel.widgets.info(id)?.loadLabel(context.packageManager)?.toString() ?: "Unavailable widget"
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Remove", color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { viewModel.removeWidget(id) }.padding(8.dp))
            }
        }
        NavRow("Add widget", onClick = onAddWidget)
    }
    if (otherProfiles.isNotEmpty()) {
        Group("Work profile") {
            otherProfiles.forEach { user ->
                val paused = remember(profileVersion, user) { userManager.isQuietModeEnabled(user) }
                InfoRow("Work apps", if (paused) "Paused" else "Active")
                NavRow(if (paused) "Resume work apps" else "Pause work apps") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        runCatching { userManager.requestQuietModeEnabled(!paused, user) }
                    }
                    profileVersion++
                }
            }
            Hint("Work apps appear in the drawer with a 💼 and a badge on their icon.")
        }
    }
    Group("Hidden apps") {
        if (hidden.isEmpty()) Hint("Long-press an app in the drawer and choose \"Hide app\".")
        hidden.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Unhide", color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { viewModel.unhide(app) }.padding(8.dp))
            }
        }
    }
}

@Composable
private fun BackupPage(viewModel: LauncherViewModel, settings: LauncherSettings, update: UpdateState) {
    val context = LocalContext.current
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
    val installedBuild = remember { UpdateChecker.installedBuild(context) }

    Group("Backup") {
        NavRow("Save a backup file") { exportLauncher.launch("rups-launcher-backup.json") }
        NavRow("Restore from a backup file") { importLauncher.launch(arrayOf("*/*")) }
        backupStatus?.let { Hint(it) }
        Hint("Favourites, folders, hidden apps and settings. Logins and the GitHub token are never included.")
    }
    Group("Updates") {
        InfoRow("Installed", "build $installedBuild")
        SwitchRow("Check automatically", settings.autoCheckUpdates) { v -> viewModel.update { it.copy(autoCheckUpdates = v) } }
        if (settings.autoCheckUpdates) {
            SwitchRow("Download new builds on Wi-Fi", settings.autoDownloadUpdates) { v ->
                viewModel.update { it.copy(autoDownloadUpdates = v) }
            }
        }
        NavRow("Check for updates now") { viewModel.checkForUpdate(force = true) }
        when (update) {
            is UpdateState.Checking -> Hint("Checking…")
            is UpdateState.UpToDate -> Hint("You are up to date (build ${update.installed}).")
            is UpdateState.Available -> Hint("Build ${update.info.build} is available.")
            is UpdateState.Downloading -> Hint("Downloading build ${update.info.build}… ${update.percent}%")
            is UpdateState.Ready -> Hint("Build ${update.info.build} is downloaded and ready.")
            is UpdateState.Installing -> Hint("Installing build ${update.info.build}…")
            is UpdateState.Failed -> Hint(update.reason)
            UpdateState.Idle -> Unit
        }
        if (update is UpdateState.Available || update is UpdateState.Ready || (update is UpdateState.Failed && update.info != null)) {
            NavRow("Install update") { viewModel.installUpdate() }
        }
        Hint(
            "Android always asks you to confirm an install, and the first time asks you to allow " +
                "\"Install unknown apps\" for this launcher."
        )
    }
    Group("GitHub token (optional)") {
        Field(settings.updateToken, "Read-only token", password = true) { v -> viewModel.update { it.copy(updateToken = v) } }
        Hint(
            "Only needed while the repository is private. Create a fine-grained token with read-only access to " +
                "this repository's contents. It is stored on this device only."
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

private fun labelFor(key: String, apps: List<AppInfo>): String =
    GestureActions.builtInLabel(key) ?: apps.firstOrNull { it.key == key }?.label ?: "Nothing"

/** Page title bar: an optional back chevron on the left, an optional action (Done) on the right. */
@Composable
private fun Header(
    title: String,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (backLabel != null) {
            Text(
                backLabel,
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            Text(actionLabel, color = Accent, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

/** A tappable card on the front page: icon, title, one line saying what is inside. */
@Composable
private fun CategoryRow(icon: String, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(CardColor, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.09f), CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 24.sp)
    }
}

/** A titled, rounded card holding related rows. */
@Composable
private fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().background(CardColor, CardShape).padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
private fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A row that does something or opens something; [value] shows the current choice on the right. */
@Composable
private fun NavRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        Text("›", color = Color.White.copy(alpha = 0.4f), fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A read-only line: a label and its current state. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

@Composable
private fun SliderRow(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Accent, fontSize = 18.sp)
    }
}

@Composable
private fun Field(value: String, placeholder: String, password: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
