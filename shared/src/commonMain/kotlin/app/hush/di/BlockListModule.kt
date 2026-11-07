// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.blocklists.BlockListRepository
import app.hush.blocklists.RoomBlockListRepository
import app.hush.blocklists.ui.BlockListViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

// BlockerSetup (the installed apps) comes from the platform module.
val blockListModule =
    module {
        singleOf(::RoomBlockListRepository) bind BlockListRepository::class
        viewModelOf(::BlockListViewModel)
    }
