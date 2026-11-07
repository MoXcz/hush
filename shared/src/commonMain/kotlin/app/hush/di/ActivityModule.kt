// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.activities.ActivityRepository
import app.hush.activities.RoomActivityRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val activityModule =
    module {
        singleOf(::RoomActivityRepository) bind ActivityRepository::class
    }
