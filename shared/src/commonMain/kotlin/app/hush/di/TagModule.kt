// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.tags.RoomTagRepository
import app.hush.tags.TagRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val tagModule =
    module {
        singleOf(::RoomTagRepository) bind TagRepository::class
    }
