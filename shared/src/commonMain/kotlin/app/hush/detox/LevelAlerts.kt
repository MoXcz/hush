// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

/** Tells the user a level was passed while the app may be in the background. */
interface LevelAlerts {
    /** [advanced]: longer than any level passed before, so progress moved on. */
    fun levelPassed(
        levelMinutes: Int,
        advanced: Boolean,
    )

    /** Removes a level alert that is still showing. */
    fun clear()
}
