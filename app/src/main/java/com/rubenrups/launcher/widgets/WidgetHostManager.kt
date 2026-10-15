package com.rubenrups.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/**
 * Hosts regular Android widgets. Adding one is a small state machine, because Android makes us ask
 * permission to bind it and then optionally run the widget's own configuration screen; both answer via
 * onActivityResult, so the in-flight widget id is parked in [pendingId] until that comes back.
 */
class WidgetHostManager(private val context: Context) {

    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    private var pendingId = -1
    private var onAdded: ((Int) -> Unit)? = null

    /** Widgets only receive updates while the host is listening: call from onStart / onStop. */
    fun startListening() { runCatching { host.startListening() } }

    fun stopListening() { runCatching { host.stopListening() } }

    fun installedProviders(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(viewContext, id, info)
    }

    /** Starts adding [provider]; [onAdded] is called with the new widget id once it is fully set up. */
    fun beginAdd(activity: Activity, provider: AppWidgetProviderInfo, onAdded: (Int) -> Unit) {
        cancelPending()
        val id = host.allocateAppWidgetId()
        pendingId = id
        this.onAdded = onAdded
        if (manager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrFinish(activity, id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            runCatching { activity.startActivityForResult(intent, REQUEST_BIND) }.onFailure { cancelPending() }
        }
    }

    /** Feed this from Activity.onActivityResult. Returns true if the result was ours. */
    fun handleResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        val id = pendingId
        if (id < 0) return true
        when {
            resultCode != Activity.RESULT_OK -> cancelPending()
            requestCode == REQUEST_BIND -> configureOrFinish(activity, id)
            else -> finish(id)
        }
        return true
    }

    private fun configureOrFinish(activity: Activity, id: Int) {
        if (manager.getAppWidgetInfo(id)?.configure == null) {
            finish(id)
            return
        }
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null) }
            .onFailure { cancelPending() }
    }

    private fun finish(id: Int) {
        val callback = onAdded
        pendingId = -1
        onAdded = null
        callback?.invoke(id)
    }

    private fun cancelPending() {
        if (pendingId >= 0) delete(pendingId)
        pendingId = -1
        onAdded = null
    }

    companion object {
        private const val HOST_ID = 1024
        private const val REQUEST_BIND = 7001
        private const val REQUEST_CONFIGURE = 7002
    }
}
