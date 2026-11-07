// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import app.hush.sync.SyncScheduler
import java.util.concurrent.TimeUnit

/** How the background sync asks for an unmetered Wi-Fi network on each API level. */
internal sealed interface SyncJobNetwork {
    /** API 28+: a network request (Wi-Fi transport, not metered). */
    data class Request(
        val transports: List<Int>,
        val capabilities: List<Int>,
    ) : SyncJobNetwork

    /** API 26–27: only the network type can be asked for. */
    data class Type(
        val networkType: Int,
    ) : SyncJobNetwork

    companion object {
        fun forSdk(sdkInt: Int): SyncJobNetwork =
            if (sdkInt >= Build.VERSION_CODES.P) {
                Request(listOf(NetworkCapabilities.TRANSPORT_WIFI), listOf(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            } else {
                Type(JobInfo.NETWORK_TYPE_UNMETERED)
            }
    }
}

/** Syncs every hour on unmetered Wi-Fi while paired. The job survives reboots (persisted). */
class JobSchedulerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    private val jobs: JobScheduler get() = context.getSystemService(JobScheduler::class.java)

    override fun onPairedChanged(paired: Boolean) {
        if (paired) schedule() else jobs.cancel(SYNC_JOB_ID)
    }

    /** Puts the job back if the system no longer has it while paired (and removes a stray one). */
    fun ensureScheduled(paired: Boolean) {
        val pending = jobs.getPendingJob(SYNC_JOB_ID)
        when {
            paired && pending == null -> schedule()
            !paired && pending != null -> jobs.cancel(SYNC_JOB_ID)
        }
    }

    private fun schedule() {
        val builder =
            JobInfo
                .Builder(SYNC_JOB_ID, ComponentName(context, SyncJobService::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(1))
                .setPersisted(true)
        when (val network = SyncJobNetwork.forSdk(Build.VERSION.SDK_INT)) {
            is SyncJobNetwork.Request -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val request = NetworkRequest.Builder()
                    network.transports.forEach(request::addTransportType)
                    network.capabilities.forEach(request::addCapability)
                    builder.setRequiredNetwork(request.build())
                }
            }

            is SyncJobNetwork.Type -> {
                builder.setRequiredNetworkType(network.networkType)
            }
        }
        jobs.schedule(builder.build())
    }

    companion object {
        /** `adb shell cmd jobscheduler run -f app.hush 1100` runs it now (AGENTS.md "Build and verify"). */
        const val SYNC_JOB_ID = 1100
    }
}
