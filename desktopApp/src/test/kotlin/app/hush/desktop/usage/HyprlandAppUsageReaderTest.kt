// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.core.AppDispatchers
import app.hush.data.prefs.createSettingsDataStore
import app.hush.usage.AppTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HyprlandAppUsageReaderTest {
    private val clock = MutableClock()
    private val log = FocusLog(clock)
    private val apps = Files.createTempDirectory("apps").toFile()
    private val settings = Files.createTempDirectory("settings").toFile()
    private val switch = UsageSwitch(createSettingsDataStore { settings.resolve("s.preferences_pb").absolutePath }, CoroutineScope(Job()))
    private val reader =
        HyprlandAppUsageReader(
            log,
            DesktopEntries(listOf(apps)),
            clock,
            AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined),
            switch,
        )

    init {
        runBlocking { reader.setEnabled(true) }
    }

    @Test
    fun `nothing is read while the switch is off`() =
        runBlocking {
            reader.setEnabled(false)
            assertFalse(reader.isPermitted())
            assertNull(reader.foregroundTime(clock.now(), clock.now()))
            reader.setEnabled(true)
            assertTrue(reader.isPermitted())
        }

    @Test
    fun `a phase's apps, sites and terminal programs are labelled, largest first, without Hush`() =
        runBlocking {
            File(apps, "zen.desktop").writeText("[Desktop Entry]\nName=Zen Browser\nStartupWMClass=zen\n")
            val start = clock.now()
            log.record(FocusedWindow(HUSH_WINDOW_CLASS, "Hush"))
            clock.advance(1.minutes)
            log.record(FocusedWindow("zen", "Video - YouTube — Zen Browser"))
            clock.advance(10.minutes)
            log.record(FocusedWindow("zen", "New Tab — Zen Browser"))
            clock.advance(2.minutes)
            log.record(FocusedWindow("kitty", "vim"))
            clock.advance(3.minutes)
            assertEquals(
                listOf(
                    AppTime("web:youtube", "YouTube", 10.minutes),
                    AppTime("term:vim", "Vim", 3.minutes),
                    AppTime("zen", "Zen Browser", 2.minutes),
                ),
                reader.foregroundTime(start, clock.now()),
            )
        }

    @Test
    fun `nothing seen gives an empty list`() =
        runBlocking {
            assertEquals(emptyList(), reader.foregroundTime(clock.now() - 30.seconds, clock.now()))
        }

    @Test
    fun `there is no permission and no settings page`() {
        assertTrue(reader.isPermitted())
        assertFalse(reader.openSettings())
    }
}
