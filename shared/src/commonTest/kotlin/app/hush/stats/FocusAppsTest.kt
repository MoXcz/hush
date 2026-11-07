// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.stats

import app.hush.activities.Activity
import app.hush.activities.ActivityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class FocusAppsTest {
    private val kobo = Activity(ActivityId(1), "Reading on Kobo")
    private val sketch = Activity(ActivityId(2), "Sketching")

    @Test
    fun `bars are relative to the largest line of their own kind`() {
        val uses =
            listOf(
                FocusUse.Doing(kobo, 100.minutes),
                FocusUse.App("notes", "Notes", 20.minutes),
                FocusUse.App("maps", "Maps", 10.minutes),
                FocusUse.Doing(sketch, 25.minutes),
            )
        assertEquals(listOf(1f, 1f, 0.5f, 0.25f), FocusApps.bars(uses))
    }

    @Test
    fun `the rest is scaled with the apps and capped at a full bar`() {
        val uses = listOf(FocusUse.App("notes", "Notes", 20.minutes), FocusUse.Other(10.minutes))
        assertEquals(listOf(1f, 0.5f), FocusApps.bars(uses))
        assertEquals(listOf(1f, 1f), FocusApps.bars(listOf(FocusUse.App("a", "A", 5.minutes), FocusUse.Other(30.minutes))))
    }

    @Test
    fun `activities alone fill their own bars`() {
        assertEquals(listOf(1f), FocusApps.bars(listOf(FocusUse.Doing(kobo, 3.minutes))))
        assertEquals(emptyList(), FocusApps.bars(emptyList()))
    }
}
