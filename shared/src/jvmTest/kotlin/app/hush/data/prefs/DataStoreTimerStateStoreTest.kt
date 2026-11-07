// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.prefs

import app.hush.tags.TagId
import app.hush.timer.Phase
import app.hush.timer.RunState
import app.hush.timer.TimerSnapshot
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DataStoreTimerStateStoreTest {
    private val file = Files.createTempDirectory("timer").resolve("settings.preferences_pb")
    private val store = DataStoreTimerStateStore(createSettingsDataStore { file.toString() })

    @Test
    fun `nothing stored loads as null`() =
        runTest {
            assertNull(store.load())
        }

    @Test
    fun `ready, running and paused snapshots round-trip`() =
        runTest {
            val snapshots =
                listOf(
                    TimerSnapshot(Phase.ShortBreak, 2, 5.minutes),
                    TimerSnapshot(
                        Phase.Work,
                        1,
                        25.minutes,
                        RunState.Running(Instant.parse("2026-11-05T09:25:00Z")),
                        blocking = true,
                        canStopEarly = false,
                        startedAt = Instant.parse("2026-11-05T09:00:00Z"),
                        tagId = TagId(2),
                    ),
                    TimerSnapshot(Phase.LongBreak, 4, 15.minutes, RunState.Paused(7.minutes + 30.seconds)),
                    TimerSnapshot(),
                )
            snapshots.forEach { snapshot ->
                store.save(snapshot)
                assertEquals(snapshot, store.load())
            }
        }
}
