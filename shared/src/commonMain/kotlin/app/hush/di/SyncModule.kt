// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.di

import app.hush.sync.DataStoreDeviceIdentity
import app.hush.sync.DataStorePeerStore
import app.hush.sync.DeviceIdentity
import app.hush.sync.PeerStore
import app.hush.sync.RoomSyncStore
import app.hush.sync.SyncController
import app.hush.sync.SyncStore
import app.hush.sync.ui.SyncViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

// SyncTransport, Pairing and SyncScheduler come from the platform module.
val syncModule =
    module {
        singleOf(::DataStoreDeviceIdentity) bind DeviceIdentity::class
        singleOf(::RoomSyncStore) bind SyncStore::class
        singleOf(::DataStorePeerStore) bind PeerStore::class
        singleOf(::SyncController)
        viewModelOf(::SyncViewModel)
    }
