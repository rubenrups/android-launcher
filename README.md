# Niagara Clone

A minimalist Android home-screen launcher inspired by Niagara Launcher, built with Kotlin and Jetpack Compose.

## Features (v0.1)

- Right-aligned favourites list, large text, one-handed friendly
- Swipe up for the A–Z app drawer with a draggable alphabet scrubber
- Instant search (IME "Search" launches the top match)
- Long-press menu: add/remove favourite, app info, uninstall
- Notification list and per-app badge counts (via `NotificationListenerService`)
- Media widget with play/pause/skip for the active media session
- Live app list: installs/uninstalls are picked up via `LauncherApps.Callback`

## Build

CI builds a debug APK on every push (Actions → latest run → `niagara-clone-debug-apk`).

Locally, with Android Studio or the Android SDK installed:

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and choose **Niagara Clone** as the default home app. For notifications and
media, tap "Enable notification access" on the home screen and grant access.

## Layout

| Path | Responsibility |
| --- | --- |
| `data/AppRepository` | Installed apps, launch/uninstall/app-info, icon loading |
| `data/LauncherPrefs` | Favourites persistence |
| `notifications/` | Listener service and the `NotificationStore` bridge to the UI |
| `LauncherViewModel` | UI state: favourites, drawer open/closed |
| `ui/` | `HomeScreen`, `AppDrawer` (with scrubber), theme |
