// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresApi
import app.hush.sync.lan.LocalAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface DiscoveryResult {
    data class Found(
        /** Local addresses in the order to try them; never empty. */
        val addresses: List<InetAddress>,
        val port: Int,
    ) : DiscoveryResult

    data object NotFound : DiscoveryResult

    /** The system's local-network permission is missing (Android 17+). */
    data object PermissionDenied : DiscoveryResult
}

class NsdDiscovery(
    private val context: Context,
) {
    private val nsd: NsdManager get() = context.getSystemService(NsdManager::class.java)

    suspend fun find(target: DiscoveryTarget): DiscoveryResult {
        val lock = multicastLock()
        return try {
            withTimeoutOrNull(TIMEOUT) { search(target) }
                ?: DiscoveryResult.NotFound.also { Log.d(TAG, "Discovery timed out") }
        } finally {
            lock?.release()
        }
    }

    private suspend fun search(target: DiscoveryTarget): DiscoveryResult {
        repeat(START_ATTEMPTS) { attempt ->
            // A fresh listener every time: NsdManager refuses a listener object it has seen before.
            val listener = Listener()
            try {
                nsd.discoverServices(DiscoveryRules.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: IllegalArgumentException) {
                return DiscoveryResult.NotFound
            } catch (_: SecurityException) {
                return DiscoveryResult.PermissionDenied
            }
            try {
                val resolves = mutableMapOf<String, Int>()
                for (service in listener.found) {
                    val verdict = resolve(service, target)
                    if (verdict is DiscoveryRules.Verdict.Usable) {
                        Log.d(TAG, "Discovery found the host: ${verdict.addresses.size} local address(es)")
                        return DiscoveryResult.Found(verdict.addresses, verdict.port)
                    }
                    val count = (resolves[service.serviceName] ?: 0) + 1
                    resolves[service.serviceName] = count
                    Log.d(TAG, "Discovery rejected a service (resolve $count): ${verdict ?: "resolve failed"}")
                    // A later resolve may bring the local address or the updated TXT, so try it again.
                    if (count < RESOLVE_ROUNDS) {
                        delay(RETRY_PAUSE)
                        listener.found.trySend(service)
                    }
                }
            } finally {
                stopQuietly(listener)
            }
            when (listener.startError) {
                FAILURE_PERMISSION_DENIED -> {
                    return DiscoveryResult.PermissionDenied
                }

                // The previous search may still be winding down inside the system; try once more.
                NsdManager.FAILURE_ALREADY_ACTIVE, NsdManager.FAILURE_INTERNAL_ERROR -> {
                    if (attempt < START_ATTEMPTS - 1) delay(RETRY_PAUSE)
                }

                else -> {
                    return DiscoveryResult.NotFound
                }
            }
        }
        return DiscoveryResult.NotFound
    }

    /** Null when the resolve failed or timed out without any update. */
    private suspend fun resolve(
        service: NsdServiceInfo,
        target: DiscoveryTarget,
    ): DiscoveryRules.Verdict? {
        val rejected = AtomicReference<DiscoveryRules.Verdict?>()
        val ownPrefixes = LocalAddress.interfacePrefixes()
        return withTimeoutOrNull(RESOLVE_TIMEOUT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                resolveWithCallback(service, target, ownPrefixes, rejected)
            } else {
                resolveLegacy(service, target, ownPrefixes)
            }
        } ?: rejected.get()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveWithCallback(
        service: NsdServiceInfo,
        target: DiscoveryTarget,
        ownPrefixes: List<LocalAddress.InterfacePrefix>,
        rejected: AtomicReference<DiscoveryRules.Verdict?>,
    ): DiscoveryRules.Verdict? {
        val result = CompletableDeferred<DiscoveryRules.Verdict?>()
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    result.complete(null)
                }

                // Updates arrive one address at a time, so wait for one we can use.
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    val verdict =
                        DiscoveryRules.judge(
                            serviceInfo.attributes,
                            serviceInfo.hostAddresses,
                            serviceInfo.port,
                            target,
                            ownPrefixes,
                        )
                    if (verdict is DiscoveryRules.Verdict.Usable) result.complete(verdict) else rejected.set(verdict)
                }

                override fun onServiceLost() {
                    result.complete(rejected.get())
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
        try {
            nsd.registerServiceInfoCallback(service, DIRECT, callback)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        return try {
            result.await()
        } finally {
            try {
                nsd.unregisterServiceInfoCallback(callback)
            } catch (_: IllegalArgumentException) {
                // Registration had failed, so there's nothing to unregister.
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(
        service: NsdServiceInfo,
        target: DiscoveryTarget,
        ownPrefixes: List<LocalAddress.InterfacePrefix>,
    ): DiscoveryRules.Verdict? {
        repeat(RESOLVE_ATTEMPTS) {
            val (info, error) =
                suspendCancellableCoroutine<Pair<NsdServiceInfo?, Int>> { continuation ->
                    val listener =
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo,
                                errorCode: Int,
                            ) {
                                if (continuation.isActive) continuation.resume(null to errorCode)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (continuation.isActive) continuation.resume(serviceInfo to 0)
                            }
                        }
                    try {
                        nsd.resolveService(service, listener)
                    } catch (_: IllegalArgumentException) {
                        continuation.resume(null to NsdManager.FAILURE_INTERNAL_ERROR)
                    }
                }
            if (info != null) {
                val host = info.host ?: return null
                return DiscoveryRules.judge(info.attributes, listOf(host), info.port, target, ownPrefixes)
            }
            // Before Android 14 only one resolve runs at a time, and a timed-out one may still be pending.
            if (error != NsdManager.FAILURE_ALREADY_ACTIVE) return null
            delay(RETRY_PAUSE)
        }
        return null
    }

    private fun stopQuietly(listener: Listener) {
        if (listener.startError != null) return
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Discovery never started, or already stopped.
        }
    }

    private fun multicastLock(): WifiManager.MulticastLock? {
        val extension =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) else 0
        if (!DiscoveryRules.needsMulticastLock(Build.VERSION.SDK_INT, extension)) return null
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return wifi.createMulticastLock("hush-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private class Listener : NsdManager.DiscoveryListener {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        @Volatile var startError: Int? = null

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            found.trySend(serviceInfo)
        }

        override fun onStartDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) {
            startError = errorCode
            found.close()
        }

        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

        override fun onStopDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) = Unit
    }

    private companion object {
        val TIMEOUT = 10.seconds
        val RESOLVE_TIMEOUT = 3.seconds
        val RETRY_PAUSE = 300.milliseconds
        const val START_ATTEMPTS = 2
        const val RESOLVE_ATTEMPTS = 3

        // Resolves per service in one search; the overall timeout still bounds them.
        const val RESOLVE_ROUNDS = 3
        const val TAG = "HushSync"

        // NsdManager.FAILURE_PERMISSION_DENIED, added in API 37; the value is fixed.
        const val FAILURE_PERMISSION_DENIED = 7
        val DIRECT = Executor { it.run() }
    }
}
