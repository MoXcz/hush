// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.blocker.Blocker
import app.hush.blocker.SessionStatus
import app.hush.core.DeviceFeatures
import app.hush.data.prefs.SETTINGS_FILE_NAME
import app.hush.desktop.di.desktopModule
import app.hush.desktop.usage.FocusTracking
import app.hush.desktop.usage.HyprlandWatcher
import app.hush.di.sharedModules
import app.hush.usage.AppUsageReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopModuleTest {
    private val hyprland = mapOf("XDG_RUNTIME_DIR" to "/run/user/1000", "HYPRLAND_INSTANCE_SIGNATURE" to "test")

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `shared and desktop modules have all their dependencies`() {
        val directory = Files.createTempDirectory("hush").toFile()
        for (environment in listOf(emptyMap(), hyprland)) {
            module { includes(sharedModules + desktopModule(directory, directory.resolve(SETTINGS_FILE_NAME), environment)) }.verify(
                // DesktopSyncHost's directory and host name, and the Hyprland socket path, are passed in.
                extraTypes = listOf(CoroutineDispatcher::class, File::class, Function0::class, Path::class, Flow::class),
            )
        }
    }

    @Test
    fun `apps are recorded only in a Hyprland session`() {
        val directory = Files.createTempDirectory("hush").toFile()
        val outside =
            koinApplication {
                modules(
                    sharedModules + desktopModule(directory, directory.resolve(SETTINGS_FILE_NAME), emptyMap()),
                )
            }.koin
        assertFalse(outside.get<DeviceFeatures>().appUsage)
        assertIs<NoAppUsage>(outside.get<AppUsageReader>())
        assertNull(outside.getOrNull<HyprlandWatcher>())

        val inside =
            koinApplication {
                modules(
                    sharedModules + desktopModule(directory, directory.resolve(SETTINGS_FILE_NAME), hyprland),
                )
            }.koin
        assertTrue(inside.get<DeviceFeatures>().appUsage)
        // Off by default, so Settings shows Hush's own switch.
        assertTrue(inside.get<DeviceFeatures>().appUsageAccess)
        assertFalse(inside.get<AppUsageReader>().isPermitted())
        assertNotNull(inside.getOrNull<FocusTracking>())
    }

    @Test
    fun `the desktop blocker has no profiles and never reports a session`() =
        runBlocking {
            val blocker: Blocker = NoBlocker()
            assertTrue(blocker.profiles().isEmpty())
            assertEquals(SessionStatus.None, blocker.activeSession())
        }
}
