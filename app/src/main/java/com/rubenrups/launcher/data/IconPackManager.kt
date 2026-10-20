package com.rubenrups.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPack(val packageName: String, val label: String)

/**
 * Minimal ADW/Nova-compatible icon pack support: finds installed packs, parses their
 * `appfilter.xml` once, and resolves a themed drawable for a component.
 */
class IconPackManager(private val context: Context) {

    private class LoadedPack(val resources: Resources, val drawableByComponent: Map<String, String>)

    private val pm = context.packageManager
    private val packs = HashMap<String, LoadedPack?>()

    fun installedPacks(): List<IconPack> =
        PACK_ACTIONS
            .flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { IconPack(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }

    /** Blocking (parses XML on first use per pack). Returns null when the pack has no icon for [component]. */
    fun iconFor(packPackage: String, component: ComponentName): Drawable? {
        val pack = synchronized(packs) {
            if (packs.containsKey(packPackage)) packs[packPackage] else load(packPackage).also { packs[packPackage] = it }
        } ?: return null
        val name = pack.drawableByComponent["${component.packageName}/${component.className}"] ?: return null
        val id = listOf("drawable", "mipmap")
            .map { pack.resources.getIdentifier(name, it, packPackage) }
            .firstOrNull { it != 0 } ?: return null
        return runCatching { ResourcesCompat.getDrawable(pack.resources, id, null) }.getOrNull()
    }

    private fun load(packPackage: String): LoadedPack? {
        val res = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull() ?: return null
        val xmlId = res.getIdentifier("appfilter", "xml", packPackage)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            runCatching {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }.getOrNull() ?: return null
        }

        val map = HashMap<String, String>()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        normalise(component)?.let { map[it] = drawable }
                    }
                }
                event = parser.next()
            }
        }
        return LoadedPack(res, map)
    }

    /** "ComponentInfo{pkg/cls}" -> "pkg/fully.qualified.Cls" */
    private fun normalise(raw: String): String? {
        val inner = COMPONENT_INFO.find(raw)?.groupValues?.get(1) ?: return null
        val cn = ComponentName.unflattenFromString(inner) ?: return null
        return "${cn.packageName}/${cn.className}"
    }

    private companion object {
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
        )
        val COMPONENT_INFO = Regex("ComponentInfo\\{([^}]+)\\}")
    }
}
