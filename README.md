# Rups Launcher

A minimalist Android home-screen launcher built with Kotlin and Jetpack Compose.

## Features (v0.9)

- **Car mode** (formerly the dashboard): a big, glanceable second screen with glucose (graph, in-range %), navigation, media controls and quick stats. Open it from the long-press menu or assign it to any swipe (down, left, right). Tapping the glucose value opens the Libre app
- **Media thumbnail**, per-card dashboard switches, and hold-a-widget on home for Move up / Move down / Remove
- **Folder sorts** by most recent or most used, and a Folder popup settings (list or grid, columns, text size, opacity, dim), separately for the home screen and the drawer
- **Car mode automation**: open on a chosen Bluetooth device or when charging, priority-only do-not-disturb while driving, and a Call card of contact buttons (all optional settings)
- **Setup guide**: a first-run walkthrough (home app, notification access, update permission, calendar), reopenable from Settings
- **Turn arrow**: the navigation card shows the maneuver picture from the Maps notification when it can be read
- **Quick launch apps** card in car mode: big buttons for the apps you choose
- **Remember car mode**: car mode stays up when you come back from another app (and after a restart), unless turned off
- **Car mode swipes**: swipe actions inside car mode (default: down closes, left/right skip tracks), and no header or close button
- **Navigate shortcuts**: when no route is active, car mode shows a button per saved place plus Maps (add as many places as you like in Settings → Car mode)
- **Car mode extras**: hourly weather and quick-settings shortcuts; cards can be reordered (settings or hold a card), hidden, and auto-stack into two columns; the screen can stay on
- **Launch animation** (optional) and **settings search**; the drawer and dashboard buttons can also live in folders and the drawer
- **Settings redesign**: a front page of categories (Home screen, Appearance, Gestures, App drawer, Dashboard & glucose, Notifications & media, Apps & widgets, Backup & updates), each with grouped cards
- **Backgrounds and buttons**: adjustable background opacity for the drawer and dashboard, and "App drawer" / "Dashboard" buttons you can add to the favourites list
- **Swipe gestures**: swipe up, down, left and right can each open the drawer, search, the notification shade, the dashboard or any app, with a short label showing what the swipe will do
- **Attached drawer** (optional): the drawer slides up beneath the home screen as one tall page
- **Auto-update**: checks for new builds while the launcher is on screen (interval is a setting: 1 min to once a day, default 3 h), downloads them on Wi-Fi, and shows an "Install" banner on home; installs through Android's installer (you confirm). Needs "Install unknown apps" allowed once, and the GitHub token while the repo is private
- **Player buttons**: the media card shows the player's own extra buttons, such as Spotify's add to liked songs
- **Swipe feedback**: the home screen leans after your finger during a configured swipe, ticks when it counts, and the dashboard slides in from the side you swiped
- **Glucose on home**: FreeStyle Libre value, trend and age under the clock, via a LibreLinkUp follower account (display only; greyed out when older than 10 min)
- **Navigation card**: the Google Maps / Waze next-turn notification shown on home

- **Pinned shortcuts**: "Add to home screen" from Chrome (and other apps) lands in the drawer, and can be favourited, foldered or removed. Needs Rups to be the default home app.

- Home-screen folders open as a see-through popup (Settings → "Open folders as a popup"; off = expand in place)
- Pull the drawer down from the top of its list to close it: reliable from the first touch, no need to scroll first

## Features (v0.7)

- Backup and restore of favourites, folders, hidden apps and settings (Settings → Backup)
- The drawer follows your finger when you swipe up (and down to close)
- Notifications on the home card open when tapped (including Android 14+) and swipe away
- Swipe left / right on the home screen to open apps of your choice
- Two columns in landscape, press feedback and haptics on rows, notification badges in the drawer
- Optional: dark text on light wallpapers; typing on a hardware keyboard starts a search

## Features (v0.6)

- Next calendar event and weather in the home header (both optional, off by default)
- App shortcuts ("New message", …) at the top of an app's long-press menu
- Home-screen widgets (long-press the home screen → Add widget)
- Web search from the drawer when nothing matches, and a row of recent apps at the top of the drawer
- Work-profile apps in the drawer (💼, badged icons), with pause/resume in Settings
- In-app update check against GitHub releases (CI publishes every build as a pre-release)

## Features (v0.5)

- Search button on the home screen opens the app drawer with the search field focused and the keyboard up
- Folders are pinned to the top of the drawer; a "New folder" row and an "Add apps" picker make filling them quick
- Left-aligned list with app icons, battery % in the header, text shadows; folders expand in place. Settings → Layout switches to a right-aligned, large-text look
- Faster alphabet scroller: reacts instantly on touch, with pre-loaded icons

## Features (v0.3)

- Swipe down in the app drawer (from the top of the list) to close it
- Folders in the drawer and in favourites: long-press an app → Add to folder…; tap a folder to open it; rename, delete, or favourite a folder with a long-press

## Features (v0.2)

- Settings (long-press empty space on the home screen): clock/notification/media toggles, left or right alignment, favourites text size
- Drag-to-reorder favourites (long-press a favourite → Reorder)
- Hide apps from the drawer (long-press → Hide app); unhide in Settings
- Swipe down on home: notification shade, search, or nothing
- ADW/Nova-compatible icon packs for drawer icons
- Haptic, magnifying alphabet scrubber

- Right-aligned favourites list, large text, one-handed friendly
- Swipe up for the A–Z app drawer with a draggable alphabet scrubber
- Instant search (IME "Search" launches the top match)
- Long-press menu: add/remove favourite, app info, uninstall
- Notification list and per-app badge counts (via `NotificationListenerService`)
- Media widget with play/pause/skip for the active media session
- Live app list: installs/uninstalls are picked up via `LauncherApps.Callback`

## Build

CI builds a debug APK on every push. Open the repo's **Actions** tab, click the latest
"Build debug APK" run, and download `rups-launcher-debug-apk` from the **Artifacts** section
at the bottom of the run summary (a zip containing `app-debug.apk`).

Locally, with Android Studio or the Android SDK installed:

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and choose **Rups Launcher** as the default home app. For notifications and
media, tap "Enable notification access" on the home screen and grant access.

## Layout

| Path | Responsibility |
| --- | --- |
| `data/AppRepository` | Installed apps, launch/uninstall/app-info, icon loading |
| `data/LauncherPrefs` | Favourites persistence |
| `notifications/` | Listener service and the `NotificationStore` bridge to the UI |
| `LauncherViewModel` | UI state: favourites, drawer open/closed |
| `ui/` | `HomeScreen`, `AppDrawer` (with scrubber), theme |
