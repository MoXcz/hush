// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.data.prefs.DataStoreTimerStateStore
import app.hush.timer.TimerController
import app.hush.timer.TimerStateStore
import app.hush.timer.ui.TimerViewModel
import app.hush.timer.ui.dimclock.DimClockViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

// WakeupScheduler and PhaseAlerts come from the platform module.
val timerModule =
    module {
        singleOf(::DataStoreTimerStateStore) bind TimerStateStore::class
        singleOf(::TimerController)
        viewModelOf(::TimerViewModel)
        viewModelOf(::DimClockViewModel)
    }
