// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.di

import app.hush.android.alarm.AlarmWakeupScheduler
import app.hush.android.notification.NotificationLevelAlerts
import app.hush.android.notification.NotificationPermission
import app.hush.android.notification.NotificationPhaseAlerts
import app.hush.android.service.TimerServiceStarter
import app.hush.android.setup.ForegroundActivity
import app.hush.android.sync.AndroidPairing
import app.hush.android.sync.AndroidSyncTransport
import app.hush.android.sync.JobSchedulerSyncScheduler
import app.hush.android.sync.LanClients
import app.hush.android.sync.LocalNetworkPermission
import app.hush.android.sync.NsdDiscovery
import app.hush.android.usage.UsageStatsAppUsageReader
import app.hush.core.DeviceFeatures
import app.hush.data.db.createAppDatabase
import app.hush.data.prefs.createSettingsDataStore
import app.hush.detox.LevelAlerts
import app.hush.sync.Pairing
import app.hush.sync.SyncRole
import app.hush.sync.SyncScheduler
import app.hush.sync.SyncTransport
import app.hush.timer.PhaseAlerts
import app.hush.timer.WakeupScheduler
import app.hush.usage.AppUsageReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val androidModule =
    module {
        single { DeviceFeatures(blocking = true, lockScreen = true, syncRole = SyncRole.Client, appUsage = true) }
        single<AppUsageReader> { UsageStatsAppUsageReader(androidContext(), get()) }
        single { createSettingsDataStore(androidContext()) }
        single { ForegroundActivity() }
        single<WakeupScheduler> { AlarmWakeupScheduler(androidContext()) }
        single<PhaseAlerts> { NotificationPhaseAlerts(androidContext()) }
        single<LevelAlerts> { NotificationLevelAlerts(androidContext()) }
        single { createAppDatabase(androidContext(), get()) }
        single { NotificationPermission(androidContext(), get()) }
        single { TimerServiceStarter(androidContext(), get(), get(), get()) }
        // The phone is the sync client: it finds the computer, types its code and starts every sync.
        single { LocalNetworkPermission(androidContext(), get()) }
        single { NsdDiscovery(androidContext()) }
        single { LanClients(androidContext(), get()) }
        singleOf(::AndroidSyncTransport) bind SyncTransport::class
        singleOf(::AndroidPairing) bind Pairing::class
        single { JobSchedulerSyncScheduler(androidContext()) } bind SyncScheduler::class
    }
