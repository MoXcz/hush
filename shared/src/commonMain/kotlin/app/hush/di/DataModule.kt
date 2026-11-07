// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.data.db.AppDatabase
import org.koin.dsl.module

// The AppDatabase instance comes from the platform module (it owns the file path).
val dataModule =
    module {
        single { get<AppDatabase>().sessionDao() }
        single { get<AppDatabase>().tagDao() }
        single { get<AppDatabase>().blockListDao() }
        single { get<AppDatabase>().syncDao() }
        single { get<AppDatabase>().activityDao() }
        single { get<AppDatabase>().sessionAppDao() }
        single { get<AppDatabase>().activityRuleDao() }
    }
