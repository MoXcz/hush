// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import app.hush.data.prefs.createSettingsDataStore
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DataStoreDeviceIdentityTest {
    @Test
    fun `the device id is created once and then stays the same`() =
        runTest {
            val file = Files.createTempDirectory("identity").resolve("settings.preferences_pb").toString()
            val identity = DataStoreDeviceIdentity(createSettingsDataStore { file })
            val first = identity.deviceId()
            assertTrue(UUID_PATTERN.matches(first))
            assertEquals(first, identity.deviceId())
        }

    @Test
    fun `two installs get different device ids`() =
        runTest {
            val a = Files.createTempDirectory("a").resolve("settings.preferences_pb").toString()
            val b = Files.createTempDirectory("b").resolve("settings.preferences_pb").toString()
            assertNotEquals(
                DataStoreDeviceIdentity(createSettingsDataStore { a }).deviceId(),
                DataStoreDeviceIdentity(createSettingsDataStore { b }).deviceId(),
            )
        }
}

val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
