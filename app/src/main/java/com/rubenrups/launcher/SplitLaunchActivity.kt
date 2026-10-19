package com.rubenrups.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * A see-through stop-over that opens a split-screen pair, the way the open-source "Be nice" app does it: start the
 * first app, wait a moment, then start the second with FLAG_ACTIVITY_LAUNCH_ADJACENT *from an activity* (a plain
 * application context is ignored for that flag). No accessibility service is needed.
 */
class SplitLaunchActivity : Activity() {
    private var started = false

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        val first = component(EXTRA_FIRST)
        val second = component(EXTRA_SECOND)
        if (first == null || second == null) {
            finish()
            return
        }
        launch(first, adjacent = false)
        Handler(Looper.getMainLooper()).postDelayed({
            launch(second, adjacent = true)
            finish()
        }, intent.getLongExtra(EXTRA_DELAY, 600L))
    }

    private fun component(key: String): ComponentName? = intent.getStringExtra(key)?.let { ComponentName.unflattenFromString(it) }

    private fun launch(component: ComponentName, adjacent: Boolean) {
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (adjacent) launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        runCatching { startActivity(launch) }
    }

    companion object {
        const val EXTRA_FIRST = "first"
        const val EXTRA_SECOND = "second"
        const val EXTRA_DELAY = "delay"
    }
}
