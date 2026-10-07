# Rups Launcher

A minimalist Android home-screen launcher built with Kotlin and Jetpack Compose.

## Features (v0.8)

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
