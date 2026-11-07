// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

class FakeLevelAlerts : LevelAlerts {
    val passed = mutableListOf<Int>()
    val advanced = mutableListOf<Boolean>()
    var clears = 0

    override fun levelPassed(
        levelMinutes: Int,
        advanced: Boolean,
    ) {
        passed += levelMinutes
        this.advanced += advanced
    }

    override fun clear() {
        clears++
    }
}
