// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.usage.AppUsageRepository
import app.hush.usage.RoomAppUsageRepository
import app.hush.usage.WorkUsageRecorder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

// AppUsageReader comes from the platform module.
val usageModule =
    module {
        singleOf(::RoomAppUsageRepository) bind AppUsageRepository::class
        singleOf(::WorkUsageRecorder)
    }
