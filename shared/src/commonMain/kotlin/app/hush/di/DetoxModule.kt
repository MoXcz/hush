// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.detox.DetoxController
import app.hush.detox.ui.PathViewModel
import app.hush.panic.ui.PanicViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// LevelAlerts and WakeupScheduler come from the platform module.
val detoxModule =
    module {
        singleOf(::DetoxController)
        viewModelOf(::PathViewModel)
        viewModelOf(::PanicViewModel)
    }
