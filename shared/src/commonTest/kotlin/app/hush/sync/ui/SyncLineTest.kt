// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.ui

import app.hush.sync.COMPUTER
import app.hush.sync.SyncOutcome
import app.hush.sync.SyncStatus
import app.hush.timer.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncLineTest {
    private val now = TestClock.START

    private fun line(
        lastSyncedAt: kotlin.time.Instant?,
        result: SyncOutcome?,
        syncing: Boolean = false,
    ) = syncLine(SyncStatus.Paired(COMPUTER, lastSyncedAt, result, syncing), now)

    @Test
    fun `the line says when it last synced, or why it didn't`() {
        assertEquals(SyncLine.Never, line(null, null))
        assertEquals(SyncLine.Syncing, line(now - 1.minutes, SyncOutcome.NotFound, syncing = true))
        assertEquals(SyncLine.Synced(0), line(now - 30.seconds, SyncOutcome.Synced))
        assertEquals(SyncLine.Synced(125), line(now - 125.minutes, SyncOutcome.Synced))
        assertEquals(SyncLine.NotFound, line(now - 5.minutes, SyncOutcome.NotFound))
        assertEquals(SyncLine.Rejected, line(null, SyncOutcome.Rejected))
        assertEquals(SyncLine.TooNew, line(null, SyncOutcome.TooNew))
        assertEquals(SyncLine.Failed, line(null, SyncOutcome.Failed))
    }

    @Test
    fun `a typed code is complete with twelve code characters, in any case and with dashes`() {
        assertTrue(isCompleteCode("7k2q-m9xd-4trb"))
        assertTrue(isCompleteCode("7K2Q M9XD 4TRB"))
        assertTrue(isCompleteCode("OIL0-1234-5678"))
        assertFalse(isCompleteCode("7k2q-m9xd-4tr"))
        assertFalse(isCompleteCode("7k2q-m9xd-4tru"))
        assertFalse(isCompleteCode(""))
    }
}
