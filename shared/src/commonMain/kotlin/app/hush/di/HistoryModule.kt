// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.history.RoomSessionRepository
import app.hush.history.SessionRepository
import app.hush.stats.ui.StatsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val historyModule =
    module {
        singleOf(::RoomSessionRepository) bind SessionRepository::class
        viewModelOf(::StatsViewModel)
    }
