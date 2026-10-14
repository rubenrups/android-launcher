package com.rubenrups.launcher.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubenrups.launcher.LauncherViewModel
import com.rubenrups.launcher.data.CalendarReader
import com.rubenrups.launcher.notifications.NotificationStore

/** One page of the setup guide. [done] = null for pages that only inform. */
private class Step(
    val icon: String,
    val title: String,
    val body: String,
    val done: Boolean?,
    val actionLabel: String? = null,
    val action: () -> Unit = {},
    val extraLabel: String? = null,
    val extraAction: () -> Unit = {},
)

/** True when this launcher is the one Android opens for the Home button. */
private fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

/**
 * A short first-run guide: make this the home app, allow notification access, allow installing updates, and
 * optionally the calendar. Each step shows whether it is already done and re-checks when you come back from
 * the system screen. Reachable again from Settings → Backup & updates.
 */
@Composable
fun OnboardingScreen(viewModel: LauncherViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Re-check everything whenever we return from a system screen.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listenerConnected by NotificationStore.listenerConnected.collectAsStateWithLifecycle()
    val isHome = remember(refresh) { isDefaultHome(context) }
    val canInstall = remember(refresh) { context.packageManager.canRequestPackageInstalls() }
    val calendarOk = remember(refresh) { CalendarReader.hasPermission(context) }

    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.update { it.copy(showCalendar = true) }
        refresh++
    }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val steps = listOf(
        Step(
            "👋", "Welcome to Rups Launcher",
            "A few quick steps get everything working. You can skip any of them and come back later: " +
                "Settings → Backup & updates → Setup guide.",
            done = null,
        ),
        Step(
            "🏠", "Make it your home app",
            "So the Home button and swipe-up gesture open this launcher.",
            done = isHome,
            actionLabel = if (isHome) "Done ✓" else "Choose as home app",
            action = {
                if (!isHome) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val role = context.getSystemService(RoleManager::class.java)
                        homeRole.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    } else {
                        open(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                }
            },
        ),
        Step(
            "🔔", "Allow notification access",
            "Needed for the notification list, the media player card and the navigation card. " +
                "If the switch is greyed out (common for apps installed from a file), open App info → ⋮ menu → " +
                "\"Allow restricted settings\", then try again.",
            done = listenerConnected,
            actionLabel = if (listenerConnected) "Done ✓" else "Open notification access",
            action = { if (!listenerConnected) open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            extraLabel = if (listenerConnected) null else "Open App info (for restricted settings)",
            extraAction = {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
        ),
        Step(
            "⬇️", "Allow updates",
            "To install new versions of the launcher from the in-app banner, Android needs you to allow " +
                "\"Install unknown apps\" for it. You still confirm every install.",
            done = canInstall,
            actionLabel = if (canInstall) "Done ✓" else "Allow installing updates",
            action = {
                if (!canInstall) {
                    open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }
            },
        ),
        Step(
            "📅", "Calendar (optional)",
            "Shows your next event under the clock. Only read on this device.",
            done = calendarOk,
            actionLabel = if (calendarOk) "Done ✓" else "Allow calendar",
            action = { if (!calendarOk) calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        ),
        Step(
            "🚀", "You're all set",
            "Swipe up for the app drawer, long-press the home screen for the menu, and open Settings for glucose, " +
                "car mode, gestures and everything else.",
            done = null,
        ),
    )
    val current = steps[step.coerceIn(0, steps.lastIndex)]
    val last = step >= steps.lastIndex

    BackHandler { if (step > 0) step-- else onFinish() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D10))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) {
                Text("Skip setup", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.clickable(onClick = onFinish).padding(8.dp))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(current.icon, fontSize = 64.sp)
            Spacer(Modifier.height(20.dp))
            Text(current.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(current.body, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            if (current.actionLabel != null) {
                Spacer(Modifier.height(28.dp))
                val finished = current.done == true
                Text(
                    current.actionLabel,
                    color = if (finished) Color(0xFF7BE0A0) else Color(0xFF14181B),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(if (finished) Color.White.copy(alpha = 0.08f) else Color.White, RoundedCornerShape(50))
                        .clickable(enabled = !finished, onClick = current.action)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
            if (current.extraLabel != null) {
                Text(
                    current.extraLabel,
                    color = Accent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = current.extraAction).padding(16.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (step > 0) "Back" else "",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).clickable(enabled = step > 0) { step-- }.padding(12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 10.dp else 7.dp).background(Color.White.copy(alpha = if (i == step) 1f else 0.3f), CircleShape))
                }
            }
            Text(
                if (last) "Finish" else "Next",
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).clickable { if (last) onFinish() else step++ }.padding(12.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}
