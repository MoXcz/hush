// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.usage

import app.hush.timer.TestClock.Companion.START
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class WorkUsageRecorderTest {
    private val reader = FakeAppUsageReader(apps = listOf(AppTime("org.notes", "Notes", 4.minutes), AppTime("org.maps", "Maps", 1.minutes)))
    private val repository = FakeAppUsageRepository()

    @Test
    fun `recording the same work phase again doesn't duplicate its apps`() =
        runTest {
            val recorder = WorkUsageRecorder(reader, repository, backgroundScope)
            recorder.capture("s1", START, START + 25.minutes)
            recorder.capture("s1", START, START + 25.minutes)
            assertEquals(listOf("org.notes", "org.maps"), repository.usage.value.map { it.packageName })
        }

    @Test
    fun `nothing is read or stored without usage access`() =
        runTest {
            reader.permitted = false
            WorkUsageRecorder(reader, repository, backgroundScope).capture("s1", START, START + 25.minutes)
            assertTrue(reader.reads.isEmpty())
            assertTrue(repository.usage.value.isEmpty())
        }

    @Test
    fun `a phase with no apps stores nothing`() =
        runTest {
            reader.apps = emptyList()
            WorkUsageRecorder(reader, repository, backgroundScope).capture("s1", START, START + 25.minutes)
            assertTrue(repository.usage.value.isEmpty())
        }
}
