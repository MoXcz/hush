// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import app.hush.sync.DeviceIdentity
import app.hush.sync.lan.LocalDevice
import app.hush.sync.lan.SyncClient
import app.hush.sync.lan.SyncIdentity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.Socket
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// Carries the job's network so a background sync's sockets are bound to it.
class JobNetwork(
    val network: Network?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<JobNetwork>
}

// Reads the key from disk: call off the main thread.
class LanClients(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity,
) {
    private val mutex = Mutex()
    private var identity: SyncIdentity? = null

    suspend fun client(): SyncClient? {
        val identity = identity() ?: return null
        val network = currentCoroutineContext()[JobNetwork]?.network
        val self = LocalDevice(deviceId = deviceIdentity.deviceId(), name = Build.MODEL)
        return SyncClient(identity, self, newSocket = { Socket().also { network?.bindSocket(it) } })
    }

    // Only there may a remembered address still be right.
    suspend fun onWifiOrEthernet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = currentCoroutineContext()[JobNetwork]?.network ?: connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun identity(): SyncIdentity? =
        mutex.withLock {
            identity ?: try {
                SyncIdentity.loadOrCreate(File(context.noBackupFilesDir, "sync")).also { identity = it }
            } catch (_: IOException) {
                null
            }
        }
}
