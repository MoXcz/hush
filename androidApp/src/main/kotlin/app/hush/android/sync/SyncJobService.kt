// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import app.hush.sync.SyncController
import app.hush.sync.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Runs in the application scope, so a sync isn't tied to the job's lifetime beyond onStopJob.
class SyncJobService :
    JobService(),
    KoinComponent {
    private val controller: SyncController by inject()
    private val permission: LocalNetworkPermission by inject()
    private val scope: CoroutineScope by inject()
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        // Without the local-network permission there's nothing to find; skip quietly.
        if (!permission.granted) return false
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) params.network else null
        running =
            scope.launch(JobNetwork(network)) {
                try {
                    controller.syncNow(SyncTrigger.Background)
                } finally {
                    // After onStopJob the system has already let go of the job.
                    if (isActive) jobFinished(params, false)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}
