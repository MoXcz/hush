// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.core.AppDispatchers
import app.hush.core.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Clock

val coreModule =
    module {
        single { AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default) }
        // The only CoroutineScope in the graph: long-lived work (the timer) runs here.
        singleOf(::ApplicationScope) binds arrayOf(ApplicationScope::class, CoroutineScope::class)
        single<Clock> { Clock.System }
        // A factory, so a time zone change on the device is picked up by the next screen that asks.
        factory { TimeZone.currentSystemDefault() }
    }

/**
 * Every shared module. Platform apps add Blocker, BlockerSetup, the settings DataStore,
 * AppDatabase, WakeupScheduler, PhaseAlerts, LevelAlerts and AppUsageReader.
 */
val sharedModules =
    listOf(
        coreModule,
        dataModule,
        settingsModule,
        tagModule,
        activityModule,
        ruleModule,
        usageModule,
        blockListModule,
        historyModule,
        timerModule,
        detoxModule,
        syncModule,
    )
