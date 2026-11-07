// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import app.hush.sync.PairedPeer
import app.hush.sync.Pairing
import app.hush.sync.PairingState
import app.hush.sync.lan.PairingCodes
import app.hush.sync.lan.ShownCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class DesktopPairing(
    private val clock: Clock,
    private val scope: CoroutineScope,
) : Pairing {
    private val current = MutableStateFlow<PairingState>(PairingState.Idle)
    private val advertising = MutableStateFlow(true)
    private var expiry: Job? = null

    internal val codes =
        PairingCodes(clock = { clock.now().toEpochMilliseconds() }, onChange = ::onCodeChanged)

    override val state: StateFlow<PairingState> = current.asStateFlow()

    override val discoverable: StateFlow<Boolean> = advertising.asStateFlow()

    override fun open() {
        codes.open()
    }

    // The host shows the code; the phone types it.
    override suspend fun enterCode(code: String) = Unit

    override fun close() {
        codes.close()
    }

    internal fun setAdvertising(value: Boolean) {
        advertising.value = value
    }

    // Called once the peer is stored, before the server closes pairing.
    internal fun paired(peer: PairedPeer) {
        current.value = PairingState.Paired(peer)
    }

    private fun onCodeChanged(shown: ShownCode?) {
        synchronized(this) {
            expiry?.cancel()
            expiry = null
            if (shown == null) {
                if (current.value !is PairingState.Paired) current.value = PairingState.Idle
                return
            }
            current.value = PairingState.ShowingCode(shown.display, Instant.fromEpochMilliseconds(shown.expiresAtMillis))
            // PairingCodes only rotates when asked, so ask once the code runs out.
            expiry =
                scope.launch {
                    delay((shown.expiresAtMillis - clock.now().toEpochMilliseconds()).coerceAtLeast(0).milliseconds)
                    codes.current()
                }
        }
    }
}
