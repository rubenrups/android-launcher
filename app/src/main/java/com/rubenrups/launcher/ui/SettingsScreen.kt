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
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.AppInfo
import com.rubenrups.launcher.data.CalendarReader
import com.rubenrups.launcher.data.DashboardModules
import com.rubenrups.launcher.data.GestureActions
import com.rubenrups.launcher.data.HeaderItems
import com.rubenrups.launcher.data.HomeRecentsAlign
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
    FOCUS("Focus mode", "🎯"),
    GLUCOSE("Glucose", "🩸"),
    DASHBOARD("Car mode", "🚗"),
    NOTIFICATIONS("Notifications & media", "🔔"),
    APPS("Apps & widgets", "🧩"),
    BACKUP("Backup & updates", "☁️"),
}

/** One entry in the settings search: what it is called, where it lives, and extra words that should find it. */
private data class SettingHit(val title: String, val page: Page, val keywords: String = "")

private val SearchIndex = listOf(
    SettingHit("Clock", Page.HOME, "time header"),
    SettingHit("Header order and text", Page.HOME, "clock date time format 12 24 hour weight size order items"),
    SettingHit("Full screen", Page.APPEARANCE, "immersive hide status bar navigation bar clock battery"),
    SettingHit("Drawer layout", Page.DRAWER, "icons only grid columns names icon size"),
    SettingHit("Header layout", Page.HOME, "stacked side by side inline compact date battery time size"),
    SettingHit("Next calendar event", Page.HOME, "agenda"),
    SettingHit("Weather", Page.HOME, "city temperature fahrenheit celsius forecast"),
    SettingHit("Align to the right", Page.HOME, "left right alignment favourites"),
    SettingHit("Pin list to the bottom", Page.HOME, "top bottom anchor thumb"),
    SettingHit("Scroll the whole home screen as one", Page.HOME, "scroll page together media hidden favourites minimum height"),
    SettingHit("Two columns in landscape", Page.HOME, "grid rotate"),
    SettingHit("Open folders as a popup", Page.HOME, "inline expand folder"),
    SettingHit("Folder popup look", Page.HOME, "grid list columns opacity text size dim background"),
    SettingHit("Folder popup in the drawer", Page.DRAWER, "grid list columns opacity text size dim background"),
    SettingHit("Search button", Page.HOME, "magnifier floating"),
    SettingHit("Recent apps on the home screen", Page.HOME, "icons bottom row last used dock count alignment size gap height placement"),
    SettingHit("App drawer button in favourites", Page.HOME, "add shortcut button"),
    SettingHit("Car mode button in favourites", Page.HOME, "dashboard add shortcut button"),
    SettingHit("Presets", Page.APPEARANCE, "left aligned right aligned large text layout"),
    SettingHit("Show app icons", Page.APPEARANCE, "icons"),
    SettingHit("Text size", Page.APPEARANCE, "font favourites"),
    SettingHit("Adapt text colour to the wallpaper", Page.APPEARANCE, "dark light ink contrast"),
    SettingHit("Launch animation", Page.APPEARANCE, "motion zoom open app"),
    SettingHit("Accent colour", Page.APPEARANCE, "theme colour highlight material you wallpaper"),
    SettingHit("Font", Page.APPEARANCE, "typeface theme serif mono"),
    SettingHit("Icon pack", Page.APPEARANCE, "theme nova adw"),
    SettingHit("Focus mode", Page.FOCUS, "hide apps distraction schedule work"),
    SettingHit("Swipe up", Page.GESTURES, "gesture"),
    SettingHit("Swipe down", Page.GESTURES, "gesture notification shade"),
    SettingHit("Swipe left", Page.GESTURES, "gesture"),
    SettingHit("Swipe right", Page.GESTURES, "gesture"),
    SettingHit("Attach below the home screen", Page.DRAWER, "drawer one page slide"),
    SettingHit("Drawer background", Page.DRAWER, "opacity transparency solid"),
    SettingHit("Search extras", Page.DRAWER, "calculator conversion currency units contacts system settings"),
    SettingHit("Recent apps", Page.DRAWER, "recents"),
    SettingHit("Web search when typing", Page.DRAWER, "google duckduckgo engine"),
    SettingHit("Car mode background", Page.DASHBOARD, "dashboard opacity transparency solid"),
    SettingHit("Open car mode automatically", Page.DASHBOARD, "bluetooth charging auto open trigger car"),
    SettingHit("Do not disturb while driving", Page.DASHBOARD, "dnd silence car mode"),
    SettingHit("Call buttons", Page.DASHBOARD, "contacts phone dial car mode"),
    SettingHit("Quick launch apps", Page.DASHBOARD, "car mode app buttons maps music phone"),
    SettingHit("Swipes in car mode", Page.DASHBOARD, "gesture next track close skip"),
    SettingHit("Navigation places", Page.DASHBOARD, "home work address destination car mode navigate maps"),
    SettingHit("Remember car mode", Page.DASHBOARD, "return stay open after switching apps"),
    SettingHit("Keep screen on", Page.DASHBOARD, "awake car mode dashboard"),
    SettingHit("Car mode cards and order", Page.DASHBOARD, "dashboard reorder hide media weather navigation"),
    SettingHit("Show glucose", Page.GLUCOSE, "libre librelink librelinkup sugar diabetes"),
    SettingHit("LibreLinkUp login", Page.GLUCOSE, "email password follower account"),
    SettingHit("Tapping the glucose value opens", Page.GLUCOSE, "librelink app"),
    SettingHit("mmol/L or mg/dL", Page.GLUCOSE, "units glucose"),
    SettingHit("Glucose low and high thresholds", Page.GLUCOSE, "range alert colour"),
    SettingHit("Notification access", Page.NOTIFICATIONS, "restricted settings listener permission"),
    SettingHit("Notifications on home", Page.NOTIFICATIONS, "show list"),
    SettingHit("Media player", Page.NOTIFICATIONS, "music spotify card"),
    SettingHit("Media player look", Page.HOME, "album art artist buttons seek bar progress scrub large opacity hide paused"),
    SettingHit("Favourites layout", Page.HOME, "icons only grid columns names icon size"),
    SettingHit("Launcher settings button", Page.HOME, "gear add home screen drawer shortcut"),
    SettingHit("Navigation", Page.NOTIFICATIONS, "google maps waze turn next"),
    SettingHit("Widgets", Page.APPS, "add remove"),
    SettingHit("Work profile", Page.APPS, "pause resume work apps"),
    SettingHit("Hidden apps", Page.APPS, "unhide"),
    SettingHit("Setup guide", Page.BACKUP, "onboarding first run default home permissions"),
    SettingHit("Automatic backup", Page.BACKUP, "schedule folder daily weekly"),
    SettingHit("Save a backup file", Page.BACKUP, "export"),
    SettingHit("Restore from a backup file", Page.BACKUP, "import"),
    SettingHit("Check for updates", Page.BACKUP, "update install build"),
    SettingHit("Download new builds on Wi-Fi", Page.BACKUP, "automatic update"),
    SettingHit("Update check interval", Page.BACKUP, "how often every minute hour check updates"),
    SettingHit("GitHub token", Page.BACKUP, "private repository access"),
)

private val CardColor = Color.White.copy(alpha = 0.07f)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val visibleApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installedBuild = remember { UpdateChecker.installedBuild(context) }

    // Which category is open ("" = the front page). Saved, so rotating the phone keeps your place.
    var pageName by rememberSaveable { mutableStateOf(viewModel.takeSettingsStartPage()) }
    val startSection = rememberSaveable { viewModel.takeSettingsStartSection() }
    var query by rememberSaveable { mutableStateOf("") }
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
                    PickerSearchField(query) { query = it }
                    val hits = if (query.isBlank()) emptyList() else SearchIndex.filter { matchesQuery(query, it.title, it.keywords, it.page.title) }
                    if (query.isNotBlank()) {
                        if (hits.isEmpty()) Hint("No setting matches \"$query\".")
                        hits.forEach { hit ->
                            CategoryRow(hit.page.icon, hit.title, "in ${hit.page.title}") {
                                query = ""
                                pageName = hit.page.name
                            }
                        }
                    }
                    if (query.isBlank()) Page.entries.forEach { entry ->
                        val summary = when (entry) {
                            Page.HOME -> "Clock, weather, favourites"
                            Page.APPEARANCE -> "Text, icons, wallpaper colours"
                            Page.GESTURES -> "Swipe up, down, left, right"
                            Page.DRAWER -> "Search, recents, background"
                            Page.FOCUS -> if (focusActive) "On now" else "Hide distracting apps"
                            Page.GLUCOSE -> if (settings.glucoseEnabled) "On · LibreLinkUp" else "LibreLinkUp, units, thresholds"
                            Page.DASHBOARD -> "Big glanceable screen, cards, order"
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
                    CompositionLocalProvider(LocalSettingsSection provides startSection) {
                    when (current) {
                        Page.HOME -> HomePage(viewModel, settings)
                        Page.APPEARANCE -> AppearancePage(viewModel, settings)
                        Page.GESTURES -> GesturesPage(settings, visibleApps) { chooser = it }
                        Page.DRAWER -> DrawerPage(viewModel, settings)
                        Page.FOCUS -> FocusPage(viewModel, settings)
                        Page.GLUCOSE -> GlucosePage(viewModel, settings, visibleApps) { chooser = it }
                        Page.DASHBOARD -> DashboardPage(viewModel, settings, visibleApps) { chooser = it }
                        Page.NOTIFICATIONS -> NotificationsPage(viewModel, settings, listenerConnected)
                        Page.APPS -> AppsPage(viewModel, settings) { pickWidget = true }
                        Page.BACKUP -> BackupPage(viewModel, settings, updateState)
                    }
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
                    "carUp" -> it.copy(carSwipeUp = key)
                    "carDown" -> it.copy(carSwipeDown = key)
                    "carLeft" -> it.copy(carSwipeLeft = key)
                    "carRight" -> it.copy(carSwipeRight = key)
                    "carApp" -> it.copy(carApps = if (key.isBlank() || key in it.carApps) it.carApps else it.carApps + key)
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
                "carUp" -> "Car mode: swipe up"
                "carDown" -> "Car mode: swipe down"
                "carLeft" -> "Car mode: swipe left"
                "carRight" -> "Car mode: swipe right"
                "carApp" -> "Add to quick launch"
                else -> "Tapping the glucose value opens"
            },
            viewModel = viewModel,
            onPick = { app -> set(app?.key.orEmpty()) },
            onDismiss = { chooser = null },
            extras = when {
                side == "glucose" || side == "carApp" -> emptyList()
                side.startsWith("carUp") || side.startsWith("carDown") || side.startsWith("carLeft") || side.startsWith("carRight") -> GestureActions.carBuiltIn.map { (key, label) -> label to key }
                else -> GestureActions.builtIn.map { (key, label) -> label to key }
            },
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
        SwitchRow("Date", settings.headerShowDate) { v -> viewModel.update { it.copy(headerShowDate = v) } }
        SwitchRow("Battery", settings.headerShowBattery) { v -> viewModel.update { it.copy(headerShowBattery = v) } }
        SliderRow("Time size", "${settings.clockSizeSp} sp", settings.clockSizeSp.toFloat(), 32f..96f) { v ->
            viewModel.update { it.copy(clockSizeSp = v.toInt()) }
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
    Group("Header layout") {
        ChoiceRow("Stacked (one item per row)", selected = settings.headerLayout == "stacked") {
            viewModel.update { it.copy(headerLayout = "stacked") }
        }
        ChoiceRow("Details beside the time", selected = settings.headerLayout == "side") {
            viewModel.update { it.copy(headerLayout = "side") }
        }
        ChoiceRow("Details wrap around the time (fits the most)", selected = settings.headerLayout == "wrap") {
            viewModel.update { it.copy(headerLayout = "wrap") }
        }
        ChoiceRow("Compact: details in one wrapping row", selected = settings.headerLayout == "inline") {
            viewModel.update { it.copy(headerLayout = "inline") }
        }
        Hint("The beside, wrap-around and compact layouts take less height, so more favourites fit on screen. Wrap-around starts the details next to the time and continues under it.")
    }
    Group("Header text") {
        ChoiceRow("Time: follow the phone", selected = settings.timeFormat == "system") { viewModel.update { it.copy(timeFormat = "system") } }
        ChoiceRow("Time: 24-hour", selected = settings.timeFormat == "24h") { viewModel.update { it.copy(timeFormat = "24h") } }
        ChoiceRow("Time: 12-hour", selected = settings.timeFormat == "12h") { viewModel.update { it.copy(timeFormat = "12h") } }
        ChoiceRow("Date: Mon, Oct 19", selected = settings.dateFormat == "short") { viewModel.update { it.copy(dateFormat = "short") } }
        ChoiceRow("Date: Monday 19 October", selected = settings.dateFormat == "long") { viewModel.update { it.copy(dateFormat = "long") } }
        ChoiceRow("Date: 19 Oct", selected = settings.dateFormat == "day_month") { viewModel.update { it.copy(dateFormat = "day_month") } }
        ChoiceRow("Date: numeric (the phone's format)", selected = settings.dateFormat == "numeric") { viewModel.update { it.copy(dateFormat = "numeric") } }
        ChoiceRow("Time weight: light", selected = settings.clockWeight == "light") { viewModel.update { it.copy(clockWeight = "light") } }
        ChoiceRow("Time weight: regular", selected = settings.clockWeight == "normal") { viewModel.update { it.copy(clockWeight = "normal") } }
        ChoiceRow("Time weight: bold", selected = settings.clockWeight == "bold") { viewModel.update { it.copy(clockWeight = "bold") } }
        SliderRow("Text size under the time", "${settings.headerTextSp} sp", settings.headerTextSp.toFloat(), 12f..28f) { v ->
            viewModel.update { it.copy(headerTextSp = v.roundToInt()) }
        }
    }
    Group("Header order") {
        val order = settings.headerOrder
        order.forEachIndexed { index, key ->
            val label = HeaderItems.all.firstOrNull { it.first == key }?.second ?: key
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 18.sp,
                    modifier = Modifier.clickable(enabled = index > 0) { viewModel.moveHeaderItem(key, -1) }.padding(12.dp),
                )
                Text(
                    "▼",
                    color = if (index < order.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 18.sp,
                    modifier = Modifier.clickable(enabled = index < order.lastIndex) { viewModel.moveHeaderItem(key, 1) }.padding(12.dp),
                )
            }
        }
        Hint("Whether each one shows is set above (and under Glucose). In the stacked and beside layouts, date, battery and weather next to each other share a line.")
    }
    Group("Media player") {
        SwitchRow("Album art", settings.mediaShowArt) { v -> viewModel.update { it.copy(mediaShowArt = v) } }
        SwitchRow("Artist name", settings.mediaShowArtist) { v -> viewModel.update { it.copy(mediaShowArtist = v) } }
        SwitchRow("Previous / next buttons", settings.mediaShowSkip) { v -> viewModel.update { it.copy(mediaShowSkip = v) } }
        SwitchRow(
            "The player's extra buttons",
            settings.mediaShowExtras,
            subtitle = "Like, shuffle, radio and so on, when the app offers them",
        ) { v -> viewModel.update { it.copy(mediaShowExtras = v) } }
        SwitchRow(
            "Seek bar",
            settings.mediaSeekBar,
            subtitle = "Shows progress and lets you drag to a position, when the player allows it",
        ) { v -> viewModel.update { it.copy(mediaSeekBar = v) } }
        SwitchRow("Large card", settings.mediaLarge, subtitle = "Bigger art and text, buttons on their own row") { v ->
            viewModel.update { it.copy(mediaLarge = v) }
        }
        SwitchRow("Hide while paused", settings.mediaHideWhenPaused) { v -> viewModel.update { it.copy(mediaHideWhenPaused = v) } }
        SliderRow("Card background", "${settings.mediaOpacity}%", settings.mediaOpacity.toFloat(), 0f..100f) { v ->
            viewModel.update { it.copy(mediaOpacity = v.roundToInt()) }
        }
        Hint("Turn the media player itself on or off under Notifications.")
    }
    Group("Favourites list") {
        ChoiceRow("Layout: list (icon and name)", selected = settings.favouritesLayout == "list") {
            viewModel.update { it.copy(favouritesLayout = "list") }
        }
        ChoiceRow("Layout: icons only", selected = settings.favouritesLayout == "icons") {
            viewModel.update { it.copy(favouritesLayout = "icons") }
        }
        ChoiceRow("Layout: icon grid", selected = settings.favouritesLayout == "grid") {
            viewModel.update { it.copy(favouritesLayout = "grid") }
        }
        if (settings.favouritesLayout != "list") {
            SliderRow("Icon size", "${settings.favouritesIconDp} dp", settings.favouritesIconDp.toFloat(), 32f..80f) { v ->
                viewModel.update { it.copy(favouritesIconDp = v.roundToInt()) }
            }
        }
        if (settings.favouritesLayout == "grid") {
            SliderRow("Columns", "${settings.favouritesGridColumns}", settings.favouritesGridColumns.toFloat(), 2f..6f) { v ->
                viewModel.update { it.copy(favouritesGridColumns = v.roundToInt()) }
            }
            SwitchRow("Show names under the icons", settings.favouritesGridLabels) { v ->
                viewModel.update { it.copy(favouritesGridLabels = v) }
            }
        }
        if (settings.favouritesLayout != "list") {
            Hint("Folders open as a popup in the icon layouts. Reordering (long-press → Reorder) shows the list while you drag.")
        }
        SwitchRow("Align to the right", settings.alignEnd) { v -> viewModel.update { it.copy(alignEnd = v) } }
        SwitchRow("Pin list to the bottom", settings.anchorBottom) { v -> viewModel.update { it.copy(anchorBottom = v) } }
        SwitchRow(
            "Scroll the whole home screen as one",
            settings.homeScrollAsOne,
            subtitle = "Clock, media, widgets and favourites move together instead of the favourites having their own area",
        ) { v -> viewModel.update { it.copy(homeScrollAsOne = v) } }
        SwitchRow("Two columns in landscape", settings.twoColumnsLandscape) { v ->
            viewModel.update { it.copy(twoColumnsLandscape = v) }
        }
        SwitchRow(
            "Open folders as a popup",
            settings.folderPopupOnHome,
            subtitle = "Off: folders expand in place",
        ) { v -> viewModel.update { it.copy(folderPopupOnHome = v) } }
    }
    Group("Folder popup") {
        if (!settings.folderPopupOnHome) {
            Hint("Turn on \"Open folders as a popup\" above to use these.")
        } else {
            PopupLookRows(
                PopupLook(settings.folderPopupGrid, settings.folderPopupColumns, settings.folderPopupTextSp, settings.folderPopupOpacity, settings.folderPopupDim),
            ) { look ->
                viewModel.update {
                    it.copy(
                        folderPopupGrid = look.grid,
                        folderPopupColumns = look.columns,
                        folderPopupTextSp = look.textSp,
                        folderPopupOpacity = look.opacity,
                        folderPopupDim = look.dim,
                    )
                }
            }
        }
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
    Group("Recent apps row") {
        SwitchRow(
            "Recent apps on the home screen",
            settings.showHomeRecents,
            subtitle = "Icons only, along the bottom",
        ) { v -> viewModel.update { it.copy(showHomeRecents = v) } }
        if (settings.showHomeRecents) {
            SliderRow("Number of apps", "${settings.homeRecentsCount}", settings.homeRecentsCount.toFloat(), 3f..10f) { v ->
                viewModel.update { it.copy(homeRecentsCount = v.roundToInt()) }
            }
            listOf(
                HomeRecentsAlign.SPREAD to "Spread across the width",
                HomeRecentsAlign.START to "Left",
                HomeRecentsAlign.CENTER to "Centre",
                HomeRecentsAlign.END to "Right",
            ).forEach { (value, label) ->
                ChoiceRow(label, selected = settings.homeRecentsAlign == value) {
                    viewModel.update { it.copy(homeRecentsAlign = value) }
                }
            }
            if (settings.homeRecentsCount > 5) {
                SwitchRow(
                    "Split over two rows",
                    settings.homeRecentsTwoRows,
                    subtitle = "With more than 5 apps, show two even rows",
                ) { v -> viewModel.update { it.copy(homeRecentsTwoRows = v) } }
            }
            SliderRow("Icon size", "${settings.homeRecentsIconDp} dp", settings.homeRecentsIconDp.toFloat(), 28f..64f) { v ->
                viewModel.update { it.copy(homeRecentsIconDp = v.roundToInt()) }
            }
            if (settings.homeRecentsAlign != HomeRecentsAlign.SPREAD) {
                SliderRow("Space between icons", "${settings.homeRecentsGapDp} dp", settings.homeRecentsGapDp.toFloat(), 0f..40f) { v ->
                    viewModel.update { it.copy(homeRecentsGapDp = v.roundToInt()) }
                }
            }
            SliderRow("Height above the bottom", "${settings.homeRecentsBottomDp} dp", settings.homeRecentsBottomDp.toFloat(), 0f..160f) { v ->
                viewModel.update { it.copy(homeRecentsBottomDp = v.roundToInt()) }
            }
            Hint("The row keeps clear of the search button.")
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
    Group("Accent colour") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentOptions.forEach { option ->
                val selected = settings.accentColor == option.key
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(option.color, CircleShape)
                        .clickable { viewModel.update { it.copy(accentColor = option.key) } },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Text("✓", color = Color(0xFF14181B), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ChoiceRow("Match my wallpaper", selected = settings.accentColor == ACCENT_WALLPAPER) {
                viewModel.update { it.copy(accentColor = ACCENT_WALLPAPER) }
            }
        }
        Hint("Used for links, ticks, switches and small buttons across the launcher.")
    }
    Group("Font") {
        FontOptions.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.update { it.copy(fontFamily = option.key) } }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.label, color = Color.White, fontSize = 17.sp, fontFamily = option.family, modifier = Modifier.weight(1f))
                if (settings.fontFamily == option.key) Text("✓", color = Accent, fontSize = 18.sp)
            }
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
    Group("Full screen") {
        ChoiceRow("Off", selected = settings.fullscreenMode == "off") { viewModel.update { it.copy(fullscreenMode = "off") } }
        ChoiceRow("Hide the status bar", selected = settings.fullscreenMode == "status") { viewModel.update { it.copy(fullscreenMode = "status") } }
        ChoiceRow("Hide the status and navigation bars", selected = settings.fullscreenMode == "both") { viewModel.update { it.copy(fullscreenMode = "both") } }
        Hint("Swipe in from the screen edge to show the bars for a moment. Phones with a camera notch may keep a thin black strip at the top. Other apps are not affected.")
    }
    Group("Motion") {
        SwitchRow(
            "Launch animation",
            settings.launchAnimation,
            subtitle = "The screen shrinks slightly while an app starts",
        ) { v -> viewModel.update { it.copy(launchAnimation = v) } }
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
            "Each swipe can open the drawer, search, the notification shade, car mode or any app. " +
                "A short label shows what it will do while you swipe."
        )
    }
}

@Composable
private fun DrawerPage(viewModel: LauncherViewModel, settings: LauncherSettings) {
    Group("Layout") {
        ChoiceRow("List (icon and name)", selected = settings.drawerLayout == "list") { viewModel.update { it.copy(drawerLayout = "list") } }
        ChoiceRow("Icon grid", selected = settings.drawerLayout == "grid") { viewModel.update { it.copy(drawerLayout = "grid") } }
        if (settings.drawerLayout == "grid") {
            SliderRow("Icon size", "${settings.drawerIconDp} dp", settings.drawerIconDp.toFloat(), 32f..80f) { v ->
                viewModel.update { it.copy(drawerIconDp = v.roundToInt()) }
            }
            SliderRow("Columns", "${settings.drawerGridColumns}", settings.drawerGridColumns.toFloat(), 3f..7f) { v ->
                viewModel.update { it.copy(drawerGridColumns = v.roundToInt()) }
            }
            SwitchRow("Show names under the icons", settings.drawerGridLabels, subtitle = "Off: icons only") { v ->
                viewModel.update { it.copy(drawerGridLabels = v) }
            }
        }
    }
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
    Group("Folder popup in the drawer") {
        PopupLookRows(
            PopupLook(settings.drawerPopupGrid, settings.drawerPopupColumns, settings.drawerPopupTextSp, settings.drawerPopupOpacity, settings.drawerPopupDim),
        ) { look ->
            viewModel.update {
                it.copy(
                    drawerPopupGrid = look.grid,
                    drawerPopupColumns = look.columns,
                    drawerPopupTextSp = look.textSp,
                    drawerPopupOpacity = look.opacity,
                    drawerPopupDim = look.dim,
                )
            }
        }
    }
    SearchExtrasGroup(viewModel, settings)
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
private fun DashboardPage(viewModel: LauncherViewModel, settings: LauncherSettings, apps: List<AppInfo>, openChooser: (String) -> Unit) {
    Group("Screen") {
        SwitchRow(
            "Keep the screen on",
            settings.keepScreenOn,
            subtitle = "While car mode is open",
        ) { v -> viewModel.update { it.copy(keepScreenOn = v) } }
        SwitchRow(
            "Remember car mode",
            settings.rememberCarMode,
            subtitle = "Come back to car mode after another app, instead of the plain home screen",
        ) { v -> viewModel.update { it.copy(rememberCarMode = v) } }
        SliderRow(
            "Background",
            "${settings.dashboardOpacity}% solid",
            settings.dashboardOpacity.toFloat(),
            0f..100f,
        ) { v -> viewModel.update { it.copy(dashboardOpacity = v.toInt()) } }
        Hint("Open car mode from the long-press menu, a swipe (see Gestures), or a favourites button (see Home screen).")
    }
    Group("Swipes in car mode") {
        NavRow("Swipe up", labelFor(settings.carSwipeUp, apps)) { openChooser("carUp") }
        NavRow("Swipe down", labelFor(settings.carSwipeDown, apps)) { openChooser("carDown") }
        NavRow("Swipe left", labelFor(settings.carSwipeLeft, apps)) { openChooser("carLeft") }
        NavRow("Swipe right", labelFor(settings.carSwipeRight, apps)) { openChooser("carRight") }
        Hint(
            "There is no close button in car mode: swipe (down by default), press back, or tap empty space. " +
                "Swipes can skip tracks, close car mode or open an app."
        )
    }
    Group("Quick launch apps") {
        settings.carApps.forEachIndexed { index, key ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labelFor(key, apps), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index > 0) { viewModel.moveCarApp(index, -1) }.padding(12.dp),
                )
                Text(
                    "▼",
                    color = if (index < settings.carApps.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index < settings.carApps.lastIndex) { viewModel.moveCarApp(index, 1) }.padding(12.dp),
                )
                Text("Remove", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.removeCarApp(index) }.padding(12.dp))
            }
        }
        NavRow("Add an app") { openChooser("carApp") }
        Hint("These show as big buttons on the Apps card in car mode, for maps, music, phone and so on.")
    }
    Group("Navigation places") {
        settings.destinations.forEachIndexed { index, place ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Place ${index + 1}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index > 0) { viewModel.moveDestination(index, -1) }.padding(10.dp),
                )
                Text(
                    "▼",
                    color = if (index < settings.destinations.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = index < settings.destinations.lastIndex) { viewModel.moveDestination(index, 1) }.padding(10.dp),
                )
                Text("Remove", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.deleteDestination(index) }.padding(10.dp))
            }
            Field(place.name, "Name, e.g. Home or Gym") { v -> viewModel.updateDestination(index) { it.copy(name = v) } }
            Field(place.address, "Address") { v -> viewModel.updateDestination(index) { it.copy(address = v) } }
            Field(place.icon, "Icon (an emoji)") { v -> viewModel.updateDestination(index) { it.copy(icon = v.take(4)) } }
        }
        NavRow("Add a place") { viewModel.addDestination() }
        Hint(
            "While no route is active, the navigation card in car mode shows a button per place, plus Maps. Each " +
                "starts Google Maps driving directions to its address. Places stay on this device and are not " +
                "included in backups."
        )
    }
    CarAutomationGroups(viewModel, settings)
    Group("Notifications card") {
        SliderRow("Notifications shown", "${settings.carNotificationCount}", settings.carNotificationCount.toFloat(), 2f..8f) { v ->
            viewModel.update { it.copy(carNotificationCount = v.roundToInt()) }
        }
        Hint("Lists your latest notifications in car mode: tap one to open it, ✕ to clear it. Needs notification access (see Notifications & media).")
    }
    Group("Media card") {
        SwitchRow("Album art", settings.carMediaShowArt) { v -> viewModel.update { it.copy(carMediaShowArt = v) } }
        SwitchRow("Artist name", settings.carMediaShowArtist) { v -> viewModel.update { it.copy(carMediaShowArtist = v) } }
        SwitchRow("The player's extra buttons", settings.carMediaShowExtras, subtitle = "Like, shuffle and so on, when the app offers them") { v ->
            viewModel.update { it.copy(carMediaShowExtras = v) }
        }
        SwitchRow("Seek bar", settings.carMediaSeekBar, subtitle = "Progress and drag-to-seek, when the player allows it") { v ->
            viewModel.update { it.copy(carMediaSeekBar = v) }
        }
    }
    Group("Cards, in order") {
        val order = settings.dashboardOrder
        order.forEachIndexed { index, key ->
            val label = DashboardModules.all.firstOrNull { it.first == key }?.second ?: key
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 18.sp,
                    modifier = Modifier.clickable(enabled = index > 0) { viewModel.swapDashboardCards(key, order[index - 1]) }.padding(12.dp),
                )
                Text(
                    "▼",
                    color = if (index < order.lastIndex) Color.White else Color.White.copy(alpha = 0.2f),
                    fontSize = 18.sp,
                    modifier = Modifier.clickable(enabled = index < order.lastIndex) { viewModel.swapDashboardCards(key, order[index + 1]) }.padding(12.dp),
                )
                Text(
                    if (key in settings.dashboardHalf) "½" else "1",
                    color = if (key in settings.dashboardHalf) Accent else Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { viewModel.setDashboardCardHalf(key, key !in settings.dashboardHalf) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
                Switch(
                    checked = key !in settings.dashboardHidden,
                    onCheckedChange = { on -> viewModel.setDashboardCardHidden(key, !on) },
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                )
            }
        }
        Hint(
            "Tap 1 / ½ to make a card half width: two half cards next to each other share a row (nice for the clock and glucose). You can also hold a card in car mode to move, resize or hide it. Navigation shows the next turn while a route is active, and Home / Work / Maps buttons otherwise, " +
                "hourly weather needs the weather city (Home screen), and glucose is set up under its own category."
        )
    }
}

@Composable
private fun GlucosePage(
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    apps: List<AppInfo>,
    openChooser: (String) -> Unit,
) {
    Group("LibreLinkUp") {
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
    if (otherProfiles.isEmpty()) {
        Group("Work profile") {
            InfoRow("Status", "No work profile on this device")
            Hint(
                "A work profile is a separate space for work apps (Android Enterprise, or apps like Shelter and Island). " +
                    "If you have one, its apps show in the drawer with a 💼 badge, and you can pause or resume them here. " +
                    "Without one there is nothing to set."
            )
        }
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

    Group("Setup") {
        NavRow("Open the setup guide") { viewModel.showOnboarding() }
        Hint("Home app, notification access, update permission and calendar, step by step.")
    }
    Group("Backup") {
        NavRow("Save a backup file") { exportLauncher.launch("rups-launcher-backup.json") }
        NavRow("Restore from a backup file") { importLauncher.launch(arrayOf("*/*")) }
        backupStatus?.let { Hint(it) }
        Hint("Favourites, folders, hidden apps and settings. Logins and the GitHub token are never included.")
    }
    AutoBackupGroup(viewModel, settings)
    Group("Updates") {
        InfoRow("Installed", "build $installedBuild")
        SwitchRow("Check automatically", settings.autoCheckUpdates) { v -> viewModel.update { it.copy(autoCheckUpdates = v) } }
        if (settings.autoCheckUpdates) {
            SwitchRow("Download new builds on Wi-Fi", settings.autoDownloadUpdates) { v ->
                viewModel.update { it.copy(autoDownloadUpdates = v) }
            }
        }
    }
    if (settings.autoCheckUpdates) {
        Group("Check every") {
            UpdateIntervals.forEach { (minutes, label) ->
                ChoiceRow(label, selected = settings.updateCheckMinutes == minutes) {
                    viewModel.update { it.copy(updateCheckMinutes = minutes) }
                }
            }
            Hint(
                "Checks happen while the launcher is on screen. GitHub allows about 60 anonymous checks an hour, so " +
                    "1 minute is only safe with a token (Settings below); otherwise a check can fail until the hour is up."
            )
        }
    }
    Group("Install") {
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

/** The look of a folder popup: list or grid, how many columns, text size, background opacity and dimming (percent). */
private data class PopupLook(val grid: Boolean, val columns: Int, val textSp: Int, val opacity: Int, val dim: Int)

/** The rows that edit one [PopupLook]; the home popup and the drawer popup each keep their own. */
@Composable
private fun PopupLookRows(look: PopupLook, onChange: (PopupLook) -> Unit) {
    ChoiceRow("List", selected = !look.grid) { onChange(look.copy(grid = false)) }
    ChoiceRow("Grid of icons", selected = look.grid) { onChange(look.copy(grid = true)) }
    if (look.grid) {
        SliderRow("Columns", "${look.columns}", look.columns.toFloat(), 2f..6f) { v -> onChange(look.copy(columns = v.roundToInt())) }
    }
    SliderRow("Text size", "${look.textSp} sp", look.textSp.toFloat(), 14f..28f) { v -> onChange(look.copy(textSp = v.toInt())) }
    SliderRow("Popup background", "${look.opacity}% solid", look.opacity.toFloat(), 0f..100f) { v -> onChange(look.copy(opacity = v.toInt())) }
    SliderRow("Dim the screen behind", "${look.dim}%", look.dim.toFloat(), 0f..80f) { v -> onChange(look.copy(dim = v.toInt())) }
}

/** The choices for how often to look for a new build: minutes to label. */
private val UpdateIntervals = listOf(
    1 to "Every minute",
    5 to "Every 5 minutes",
    15 to "Every 15 minutes",
    60 to "Every hour",
    180 to "Every 3 hours",
    360 to "Every 6 hours",
    1440 to "Once a day",
)

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
/** The title of the group to scroll to when a page is opened from a shortcut (see LauncherViewModel.openSettings). */
internal val LocalSettingsSection = compositionLocalOf { "" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val target = LocalSettingsSection.current
    val isTarget = title != null && target.isNotBlank() && title.equals(target, ignoreCase = true)
    val requester = remember { BringIntoViewRequester() }
    if (isTarget) {
        LaunchedEffect(Unit) {
            // Give the page a moment to lay out before scrolling to the group.
            delay(300)
            requester.bringIntoView()
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).then(if (isTarget) Modifier.bringIntoViewRequester(requester) else Modifier)) {
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
internal fun Hint(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
internal fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
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
internal fun NavRow(label: String, value: String? = null, onClick: () -> Unit) {
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
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

@Composable
internal fun SliderRow(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Accent, fontSize = 18.sp)
    }
}

@Composable
internal fun Field(value: String, placeholder: String, password: Boolean = false, onChange: (String) -> Unit) {
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

/** Automatic backups into a folder you choose, kept to the newest five. */
@Composable
private fun AutoBackupGroup(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val status by viewModel.backupStatus.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Keep the right to write there after the picker closes (and after restarts).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.update { it.copy(autoBackupFolder = uri.toString()) }
        }
    }
    Group("Automatic backup") {
        SwitchRow("Back up automatically", settings.autoBackup) { on -> viewModel.update { it.copy(autoBackup = on) } }
        NavRow(
            "Backup folder",
            settings.autoBackupFolder.takeIf { it.isNotBlank() }?.let { Uri.parse(it).lastPathSegment } ?: "Not chosen",
        ) { folderPicker.launch(null) }
        ChoiceRow("Every day", selected = settings.autoBackupDays == 1) { viewModel.update { it.copy(autoBackupDays = 1) } }
        ChoiceRow("Every week", selected = settings.autoBackupDays == 7) { viewModel.update { it.copy(autoBackupDays = 7) } }
        NavRow("Back up now") { scope.launch { viewModel.runBackup(force = true) } }
        status?.let { Hint(it) }
        if (settings.lastBackupMs > 0) {
            Hint("Last backup: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(settings.lastBackupMs)))
        }
        Hint("Runs while the launcher is on screen, keeps the newest 5 files, and never includes your logins.")
    }
}

/** Which extra answers the drawer's search box gives besides app names. */
@Composable
private fun SearchExtrasGroup(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(searchContacts = granted) }
    }
    val context = LocalContext.current
    Group("Search extras") {
        SwitchRow("Calculator", settings.searchCalculator, subtitle = "Type 12*7 or (3+4)^2") { on -> viewModel.update { it.copy(searchCalculator = on) } }
        SwitchRow("Unit and currency conversion", settings.searchConversions, subtitle = "Type 5 km to mi, 72 f to c or 100 usd to eur") { on ->
            viewModel.update { it.copy(searchConversions = on) }
        }
        SwitchRow("Phone settings screens", settings.searchSystemScreens, subtitle = "Type wifi, battery, bluetooth…") { on ->
            viewModel.update { it.copy(searchSystemScreens = on) }
        }
        SwitchRow("Contacts", settings.searchContacts, subtitle = "Finds people by name; asks for the Contacts permission") { on ->
            if (on && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            } else {
                viewModel.update { it.copy(searchContacts = on) }
            }
        }
        Hint("Currency rates come from frankfurter.app (European Central Bank); only the amount and the two currency codes are sent.")
    }
}
