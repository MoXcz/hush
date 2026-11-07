// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android

import android.app.Application
import app.hush.android.di.androidModule
import app.hush.android.di.blockerModule
import app.hush.android.notification.TimerNotifications
import app.hush.android.service.TimerServiceStarter
import app.hush.android.setup.ForegroundActivity
import app.hush.android.sync.JobSchedulerSyncScheduler
import app.hush.di.sharedModules
import app.hush.sync.PeerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.IOException

class HushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HushApp)
            modules(sharedModules + androidModule + blockerModule)
        }
        registerActivityLifecycleCallbacks(get<ForegroundActivity>())
        TimerNotifications.createChannels(this)
        get<TimerServiceStarter>().start()
        // The sync job is persisted; this only puts it back if the system no longer has it while paired.
        get<CoroutineScope>().launch {
            try {
                get<JobSchedulerSyncScheduler>().ensureScheduled(paired = get<PeerStore>().peer() != null)
            } catch (_: IOException) {
                // Unreadable peer store: the next pairing schedules it again.
            }
        }
    }
}
