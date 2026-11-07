// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2024-2026 Nethical and Curbox contributors (Curbox: services/AppBlockerService.kt,
// services/BaseBlockingService.kt, blockers/FocusModeBlocker.kt), adapted.
package app.hush.blocker.builtin

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import app.hush.blocklists.BlockListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import kotlin.time.Clock

// Window changes only, no polling or window content; after process death the block comes back from the store.
class AppBlockerService :
    AccessibilityService(),
    KoinComponent {
    private val store: BlockSessionStore by inject()
    private val blockLists: BlockListRepository by inject()
    private val clock: Clock by inject()

    private var scope: CoroutineScope? = null
    private var alwaysAllowed: Set<String> = emptySet()
    private var alwaysAllowedAt: Long? = null
    private var block: ActiveBlock? = null
    private var foreground: String? = null
    private var lastShown: String? = null
    private var lastShownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = this
        scope?.cancel()
        scope =
            MainScope().also { scope ->
                scope.launch {
                    combine(store.session, blockLists.lists) { session, lists ->
                        session?.let { s -> ActiveBlock(s.profile, s.endsAt, lists.firstOrNull { it.id == s.profile }?.packages.orEmpty()) }
                    }.catch { if (it !is IOException) throw it }
                        .collect {
                            block = it
                            // The app in front may have become blocked (e.g. state loaded after a restart).
                            foreground?.let(::check)
                        }
                }
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        // Hush's own windows (block screen, settings) are never covered, but still count as being in
        // front, so a later list or session change can't re-check the previous app over them.
        foreground = packageName
        if (packageName == this.packageName) return
        // Leaving for another app (e.g. home after Back) ends the debounce for the blocked one.
        if (packageName != lastShown) lastShown = null
        check(packageName)
    }

    private fun check(packageName: String) {
        val block = block
        val now = SystemClock.uptimeMillis()
        // The default launcher, keyboard or dialer can change while the service runs.
        val loadedAt = alwaysAllowedAt
        if (loadedAt == null || now - loadedAt > ALLOWED_TTL_MS) {
            alwaysAllowed = AlwaysAllowed.packages(this)
            alwaysAllowedAt = now
        }
        if (!BlockRules.shouldBlock(packageName, block, clock.now(), alwaysAllowed) || block == null) return
        // An app can send several window changes while opening; cover it once.
        if (packageName == lastShown && now - lastShownAt < REPEAT_MS) return
        lastShown = packageName
        lastShownAt = now
        startActivity(BlockActivity.intent(this, packageName, block))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (running === this) running = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        private const val REPEAT_MS = 1_500L
        private const val ALLOWED_TTL_MS = 60_000L

        @Volatile
        private var running: AppBlockerService? = null

        /** Turns the running service off (removes it from the enabled list). False if it isn't running. */
        internal fun disableRunning(): Boolean {
            val service = running ?: return false
            service.disableSelf()
            return true
        }
    }
}
