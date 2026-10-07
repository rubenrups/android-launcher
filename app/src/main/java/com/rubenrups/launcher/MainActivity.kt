package com.rubenrups.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rubenrups.launcher.ui.LauncherApp
import com.rubenrups.launcher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        viewModel.widgets.startListening()
    }

    override fun onStop() {
        viewModel.widgets.stopListening()
        super.onStop()
    }

    /** With a hardware keyboard attached, typing on the home screen starts a search. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val typed = event.unicodeChar
        if (viewModel.canTypeToSearch() && event.isPrintingKey && typed != 0 &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
        ) {
            viewModel.openDrawer(focusSearch = true, initialQuery = typed.toChar().toString())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Widget binding and configuration answer through the classic result callback.
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.widgets.handleResult(this, requestCode, resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                LauncherApp(viewModel)
            }
        }
    }

    /** Pressing Home while we are already on top should return to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in (intent.categories ?: emptySet())) {
            viewModel.closeAll()
        }
    }
}
