package com.rubenrups.launcher.data

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Launcher functions that can sit in the favourites list like apps: the app drawer and car mode.
 * They are ordinary [AppInfo] entries (so ordering, removal and layout all just work), but belong to no
 * installed app: [LauncherViewModel.launch] runs them and [icon] draws their picture.
 */
object VirtualApps {
    private const val PACKAGE = "com.rubenrups.launcher"
    private const val PREFIX = "virtual."

    val drawer = AppInfo("App drawer", ComponentName(PACKAGE, PREFIX + "Drawer"), Process.myUserHandle())
    val dashboard = AppInfo("Car mode", ComponentName(PACKAGE, PREFIX + "Dashboard"), Process.myUserHandle())
    val settingsButton = AppInfo("Launcher settings", ComponentName(PACKAGE, PREFIX + "Settings"), Process.myUserHandle())
    val all = listOf(drawer, dashboard, settingsButton)

    fun byKey(key: String): AppInfo? = all.firstOrNull { it.key == key }

    fun isVirtual(app: AppInfo) = app.component.className.startsWith(PREFIX)

    /** A round dark badge with a white glyph: a 3x3 grid of dots for the drawer, four tiles for car mode, a gear for settings. */
    fun icon(app: AppInfo, sizePx: Int = 144): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF2B3036.toInt()
        canvas.drawCircle(s / 2, s / 2, s / 2, paint)
        paint.color = 0xFFFFFFFF.toInt()
        if (app.component.className == drawer.component.className) {
            val step = s * 0.2f
            for (row in -1..1) for (col in -1..1) {
                canvas.drawCircle(s / 2 + col * step, s / 2 + row * step, s * 0.055f, paint)
            }
        } else if (app.component.className == settingsButton.component.className) {
            // A gear: eight teeth around a ring.
            for (i in 0 until 8) {
                canvas.save()
                canvas.rotate(i * 45f, s / 2, s / 2)
                canvas.drawRoundRect(RectF(s / 2 - s * 0.05f, s * 0.17f, s / 2 + s * 0.05f, s * 0.3f), s * 0.02f, s * 0.02f, paint)
                canvas.restore()
            }
            canvas.drawCircle(s / 2, s / 2, s * 0.24f, paint)
            paint.color = 0xFF2B3036.toInt()
            canvas.drawCircle(s / 2, s / 2, s * 0.11f, paint)
        } else {
            val tile = s * 0.18f
            val gap = s * 0.05f
            for (row in 0..1) for (col in 0..1) {
                val left = s / 2 + (if (col == 0) -tile - gap / 2 else gap / 2)
                val top = s / 2 + (if (row == 0) -tile - gap / 2 else gap / 2)
                canvas.drawRoundRect(RectF(left, top, left + tile, top + tile), s * 0.04f, s * 0.04f, paint)
            }
        }
        return bitmap.asImageBitmap()
    }
}
