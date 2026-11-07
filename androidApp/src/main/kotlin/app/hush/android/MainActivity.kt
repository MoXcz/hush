// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hush.sync.SyncController
import app.hush.sync.SyncTrigger
import app.hush.ui.App
import app.hush.ui.navigation.OPEN_PANIC_EXTRA
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    // Set when the block screen asks for the panic flow; App acknowledges it once shown.
    private var openPanic by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openPanic = intent.wantsPanic()
        setContent { App(openPanic = openPanic, onPanicOpened = { openPanic = false }) }
    }

    override fun onStart() {
        super.onStart()
        // Throttled and a no-op while unpaired.
        get<SyncController>().launchSync(SyncTrigger.Open)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsPanic()) openPanic = true
    }

    private fun Intent.wantsPanic(): Boolean = getBooleanExtra(OPEN_PANIC_EXTRA, false)
}
