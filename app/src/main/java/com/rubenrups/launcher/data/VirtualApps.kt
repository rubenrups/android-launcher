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
 * Launcher functions that can sit in the favourites list like apps: the app drawer, car mode and saved split-screen pairs.
 * They are ordinary [AppInfo] entries (so ordering, removal and layout all just work), but belong to no
 * installed app: [LauncherViewModel.launch] runs them and [icon] draws their picture.
 */
object VirtualApps {
    private const val PACKAGE = "com.rubenrups.launcher"
    private const val PREFIX = "virtual."

    val drawer = AppInfo("App drawer", ComponentName(PACKAGE, PREFIX + "Drawer"), Process.myUserHandle())
    val dashboard = AppInfo("Car mode", ComponentName(PACKAGE, PREFIX + "Dashboard"), Process.myUserHandle())
    val all = listOf(drawer, dashboard)

    fun byKey(key: String): AppInfo? = all.firstOrNull { it.key == key }

    private const val PAIR_PREFIX = PREFIX + "Pair."

    /** A saved split-screen pair, shown like an app button. */
    fun pair(p: SplitPair) = AppInfo(p.name.ifBlank { "Split screen" }, ComponentName(PACKAGE, PAIR_PREFIX + p.id), Process.myUserHandle())

    fun isPair(app: AppInfo) = app.component.className.startsWith(PAIR_PREFIX)

    fun pairId(app: AppInfo): String = app.component.className.removePrefix(PAIR_PREFIX)

    fun isVirtual(app: AppInfo) = app.component.className.startsWith(PREFIX)

    /** A round dark badge with a white glyph: a 3x3 grid of dots for the drawer, four tiles for car mode. */
    fun icon(app: AppInfo, sizePx: Int = 144): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF2B3036.toInt()
        canvas.drawCircle(s / 2, s / 2, s / 2, paint)
        paint.color = 0xFFFFFFFF.toInt()
        if (isPair(app)) {
            // Two panes side by side.
            val w = s * 0.2f
            val h = s * 0.4f
            val gap = s * 0.04f
            canvas.drawRoundRect(RectF(s / 2 - gap - w, s / 2 - h / 2, s / 2 - gap, s / 2 + h / 2), s * 0.05f, s * 0.05f, paint)
            canvas.drawRoundRect(RectF(s / 2 + gap, s / 2 - h / 2, s / 2 + gap + w, s / 2 + h / 2), s * 0.05f, s * 0.05f, paint)
        } else if (app.component.className == drawer.component.className) {
            val step = s * 0.2f
            for (row in -1..1) for (col in -1..1) {
                canvas.drawCircle(s / 2 + col * step, s / 2 + row * step, s * 0.055f, paint)
            }
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
