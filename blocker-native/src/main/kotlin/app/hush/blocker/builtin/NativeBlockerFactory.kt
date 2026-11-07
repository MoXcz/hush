// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.hush.blocker.Blocker
import app.hush.blocker.BlockerSetup
import app.hush.blocklists.BlockListRepository
import app.hush.core.AppDispatchers
import kotlin.time.Clock

/** Builds the built-in engine's parts, so the app's Koin module needn't know their internals. */
object NativeBlockerFactory {
    fun sessionStore(dataStore: DataStore<Preferences>): BlockSessionStore = DataStoreBlockSessionStore(dataStore)

    fun blocker(
        context: Context,
        store: BlockSessionStore,
        blockLists: BlockListRepository,
        clock: Clock,
    ): Blocker = NativeBlocker(store, blockLists, clock, serviceEnabled = context::isBlockerServiceEnabled)

    fun setup(
        context: Context,
        dispatchers: AppDispatchers,
    ): BlockerSetup = NativeBlockerSetup(context, dispatchers)
}
