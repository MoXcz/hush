// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.di

import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.core.DeviceFeatures
import app.hush.data.db.createAppDatabase
import app.hush.data.prefs.createSettingsDataStore
import app.hush.desktop.DesktopAlerts
import app.hush.desktop.NoAppUsage
import app.hush.desktop.NoBlocker
import app.hush.desktop.NoWakeups
import app.hush.desktop.sync.DesktopPairing
import app.hush.desktop.sync.DesktopSyncHost
import app.hush.desktop.usage.DesktopEntries
import app.hush.desktop.usage.FocusLog
import app.hush.desktop.usage.FocusTracking
import app.hush.desktop.usage.FocusWatcher
import app.hush.desktop.usage.HyprlandAppUsageReader
import app.hush.desktop.usage.HyprlandWatcher
import app.hush.desktop.usage.UsageSwitch
import app.hush.desktop.usage.hyprlandSocketDirectory
import app.hush.detox.LevelAlerts
import app.hush.sync.NoSync
import app.hush.sync.Pairing
import app.hush.sync.SyncRole
import app.hush.sync.SyncScheduler
import app.hush.sync.SyncTransport
import app.hush.timer.PhaseAlerts
import app.hush.timer.TimerController
import app.hush.timer.WakeupScheduler
import app.hush.usage.AppUsageReader
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File

// Focused windows are recorded only in a Hyprland session.
fun desktopModule(
    dataDirectory: File,
    settingsFile: File,
    environment: Map<String, String> = System.getenv(),
) = module {
    val hyprland = hyprlandSocketDirectory(environment)
    single {
        DeviceFeatures(
            blocking = false,
            lockScreen = false,
            syncRole = SyncRole.Host,
            appUsage = hyprland != null,
            // Hush's own switch, off by default.
            appUsageAccess = true,
        )
    }
    if (hyprland != null) {
        single { FocusLog(get()) }
        single { HyprlandWatcher(hyprland, get(), get(), get(), get()) } bind FocusWatcher::class
        single { UsageSwitch(get(), get()) }
        single { FocusTracking(get<UsageSwitch>().enabled, get<TimerController>().state, get<HyprlandWatcher>(), get()) }
        single { DesktopEntries(DesktopEntries.directories(environment, System.getProperty("user.home"))) }
        singleOf(::HyprlandAppUsageReader) bind AppUsageReader::class
    } else {
        single<AppUsageReader> { NoAppUsage() }
    }
    single { createSettingsDataStore { settingsFile.absolutePath } }
    single { createAppDatabase(dataDirectory, get()) }
    singleOf(::NoBlocker) binds arrayOf(Blocker::class, BlockerSetup::class)
    single<WakeupScheduler> { NoWakeups() }
    singleOf(::DesktopAlerts) binds arrayOf(PhaseAlerts::class, LevelAlerts::class)
    // The desktop is the sync host: it shows the code and answers; it never starts a sync itself.
    singleOf(::DesktopPairing) bind Pairing::class
    single { NoSync() } binds arrayOf(SyncTransport::class, SyncScheduler::class)
    single { DesktopSyncHost(dataDirectory.resolve("sync"), get(), get(), get(), get(), get(), get()) }
}
