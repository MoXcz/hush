// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Timer : Route

    /** Full-screen dim countdown. Not a tab: it sits on top of the timer. */
    @Serializable
    data object DimClock : Route

    @Serializable
    data object Path : Route

    /** The panic flow's grounding exercise. Sits on top of the path. */
    @Serializable
    data object Grounding : Route

    /** Focus time by tag, detox progress and the history list. Sits on top of the path. */
    @Serializable
    data object Stats : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Onboarding : Route

    /** Pairing and sync status with the other device. Sits on top of settings. */
    @Serializable
    data object Sync : Route

    /** The app picker of one block list ([id] is its profile id). Sits on top of settings. */
    @Serializable
    data class BlockList(
        val id: String,
    ) : Route
}

/** Launch extra (boolean) that opens the panic flow, sent by the blocker's block screen. */
const val OPEN_PANIC_EXTRA = "app.hush.extra.OPEN_PANIC"
