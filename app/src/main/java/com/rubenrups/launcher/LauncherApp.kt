package com.rubenrups.launcher

import android.app.Application
import com.rubenrups.launcher.data.Diagnostics

class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }
}
