// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hush.blocker.ui.BlockedScreen
import app.hush.blocklists.ProfileRole
import app.hush.ui.navigation.OPEN_PANIC_EXTRA
import app.hush.ui.theme.HushTheme
import kotlin.time.Instant

/** Covers a blocked app. Back goes home. */
class BlockActivity : ComponentActivity() {
    private var blocked by mutableStateOf<Blocked?>(null)

    private data class Blocked(
        val appLabel: String?,
        val endsAt: Instant,
        val detox: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        blocked = intent.toBlocked()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )
        setContent {
            HushTheme {
                blocked?.let {
                    BlockedScreen(
                        appLabel = it.appLabel,
                        endsAt = it.endsAt,
                        detox = it.detox,
                        onBack = ::goHome,
                        onPanic = ::openPanic,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blocked = intent.toBlocked()
    }

    private fun Intent.toBlocked(): Blocked =
        Blocked(
            appLabel = getStringExtra(EXTRA_PACKAGE)?.let(packageManager::labelOf),
            endsAt = Instant.fromEpochMilliseconds(getLongExtra(EXTRA_ENDS_AT, 0)),
            detox = getBooleanExtra(EXTRA_DETOX, false),
        )

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun openPanic() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return goHome()
        startActivity(
            launch
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_PANIC_EXTRA, true),
        )
        finishAndRemoveTask()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ENDS_AT = "ends_at"
        private const val EXTRA_DETOX = "detox"

        internal fun intent(
            context: Context,
            packageName: String,
            block: ActiveBlock,
        ): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_ENDS_AT, block.endsAt.toEpochMilliseconds())
                .putExtra(EXTRA_DETOX, block.profile == ProfileRole.Detox.profileId)
    }
}
