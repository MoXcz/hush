// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import androidx.datastore.core.DataStore
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.core.DeviceFeatures
import app.hush.data.db.AppDatabase
import app.hush.detox.LevelAlerts
import app.hush.sync.Pairing
import app.hush.sync.SyncScheduler
import app.hush.sync.SyncTransport
import app.hush.timer.PhaseAlerts
import app.hush.timer.WakeupScheduler
import app.hush.usage.AppUsageReader
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `shared modules have all their dependencies`() {
        // Platform-provided types: bound in androidApp's and desktopApp's modules.
        val platformTypes =
            listOf(
                CoroutineDispatcher::class,
                Blocker::class,
                BlockerSetup::class,
                DataStore::class,
                WakeupScheduler::class,
                PhaseAlerts::class,
                LevelAlerts::class,
                AppDatabase::class,
                DeviceFeatures::class,
                SyncTransport::class,
                Pairing::class,
                SyncScheduler::class,
                AppUsageReader::class,
                // BlockListViewModel's list id, passed as a parameter.
                String::class,
            )
        module { includes(sharedModules) }.verify(extraTypes = platformTypes)
    }
}
