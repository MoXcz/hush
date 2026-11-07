// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** Set by [App] around the navigation host. Null in previews, where shared elements are skipped. */
internal val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Lets the timer ring and countdown move between the timer and the dim clock. */
@Composable
internal fun Modifier.sharedTimerElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedContentScope.current
    // Default bounds transform, as Tomato: the expressive fast spring bounced the ring past its target.
    return with(shared) { this@sharedTimerElement.sharedBounds(rememberSharedContentState(key), animated) }
}

internal object SharedTimerKeys {
    const val RING = "timer-ring"
    const val COUNTDOWN = "timer-countdown"
}
