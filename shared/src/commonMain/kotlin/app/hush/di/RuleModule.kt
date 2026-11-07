// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.rules.ActivityRuleRepository
import app.hush.rules.RoomActivityRuleRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ruleModule =
    module {
        singleOf(::RoomActivityRuleRepository) bind ActivityRuleRepository::class
    }
