// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.di

import app.hush.android.BuildConfig
import app.hush.blocker.BlockProfile
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocker.builtin.NativeBlockerFactory
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.ProfileRole
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

// The only place outside :blocker-native that names the engine (AGENTS.md §3).
val blockerModule =
    if (BuildConfig.FAKE_BLOCKER) {
        module {
            single {
                FakeBlocker(profiles = ProfileRole.entries.map { BlockProfile(it.profileId, it.name, canStopEarly = true) })
            } binds arrayOf(Blocker::class, BlockerSetup::class)
        }
    } else {
        module {
            // The service reads the same store, so blocks survive process death.
            single { NativeBlockerFactory.sessionStore(get()) }
            single { NativeBlockerFactory.blocker(androidContext(), get(), get(), get()) }
            single { NativeBlockerFactory.setup(androidContext(), get()) }
        }
    }
