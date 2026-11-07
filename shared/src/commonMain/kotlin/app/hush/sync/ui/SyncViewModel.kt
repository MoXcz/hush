// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.core.DeviceFeatures
import app.hush.sync.Pairing
import app.hush.sync.PairingFailure
import app.hush.sync.PairingState
import app.hush.sync.SyncController
import app.hush.sync.SyncRole
import app.hush.sync.SyncStatus
import app.hush.sync.SyncTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/** Client pairing progress. */
enum class PairingStep { Ready, Searching, Connecting }

@Immutable
data class SyncUiState(
    val role: SyncRole = SyncRole.None,
    val loading: Boolean = true,
    /** The paired device's name; null while unpaired. */
    val peerName: String? = null,
    val line: SyncLine? = null,
    /** Host, unpaired: the code to type (`XXXX-XXXX-XXXX`), and how long it stays valid. */
    val code: String? = null,
    val secondsLeft: Int = 0,
    /** Client, unpaired. */
    val codeInput: String = "",
    val step: PairingStep = PairingStep.Ready,
    /** Why the last pairing attempt didn't work; acknowledged with [SyncAction.DismissProblem]. */
    val problem: PairingFailure? = null,
    /** Host: false while the phone can't find this device on the network. */
    val discoverable: Boolean = true,
) {
    val paired: Boolean get() = peerName != null

    val canPair: Boolean get() = step == PairingStep.Ready && isCompleteCode(codeInput)
}

sealed interface SyncAction {
    data class EditCode(
        val code: String,
    ) : SyncAction

    data object Pair : SyncAction

    data object Unpair : SyncAction

    data object DismissProblem : SyncAction
}

/** Characters in a pairing code: Crockford base32, which reads O as 0 and I or L as 1. */
private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZOIL"
const val PAIRING_CODE_LENGTH = 12

/** Whether [input] looks like a whole code (any case, dashes and spaces allowed). The transport decides. */
fun isCompleteCode(input: String): Boolean {
    val chars = input.uppercase().filterNot { it == '-' || it == ' ' }
    return chars.length == PAIRING_CODE_LENGTH && chars.all { it in CODE_ALPHABET }
}

class SyncViewModel(
    private val controller: SyncController,
    private val pairing: Pairing,
    features: DeviceFeatures,
    private val clock: Clock,
) : ViewModel() {
    private val role = features.syncRole
    private val codeInput = MutableStateFlow("")

    // Ticks once a second only while a code is shown, so the countdown moves and then stops.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ticks: Flow<Unit> =
        pairing.state.transformLatest { state ->
            emit(Unit)
            if (state is PairingState.ShowingCode) {
                while (clock.now() < state.expiresAt) {
                    delay(1.seconds)
                    emit(Unit)
                }
            }
        }

    val uiState: StateFlow<SyncUiState> =
        combine(controller.status, pairing.state, codeInput, pairing.discoverable, ticks) { status, pairingState, input, discoverable, _ ->
            val state =
                when (status) {
                    SyncStatus.Loading -> {
                        SyncUiState(role = role)
                    }

                    is SyncStatus.Paired -> {
                        SyncUiState(role = role, loading = false, peerName = status.peer.name, line = syncLine(status, clock.now()))
                    }

                    SyncStatus.Unpaired -> {
                        val shown = pairingState as? PairingState.ShowingCode
                        SyncUiState(
                            role = role,
                            loading = false,
                            code = shown?.code,
                            secondsLeft =
                                shown?.let { ceil((it.expiresAt - clock.now()).toDouble(DurationUnit.SECONDS)).toInt().coerceAtLeast(0) }
                                    ?: 0,
                            codeInput = input,
                            step =
                                when (pairingState) {
                                    PairingState.Searching -> PairingStep.Searching
                                    PairingState.Connecting -> PairingStep.Connecting
                                    else -> PairingStep.Ready
                                },
                            problem = (pairingState as? PairingState.Failed)?.reason,
                        )
                    }
                }
            state.copy(discoverable = discoverable)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState(role = role))

    init {
        if (role != SyncRole.None) {
            viewModelScope.launch {
                controller.status
                    .map {
                        when (it) {
                            SyncStatus.Loading -> null
                            SyncStatus.Unpaired -> false
                            is SyncStatus.Paired -> true
                        }
                    }.filterNotNull()
                    .distinctUntilChanged()
                    .collect { paired ->
                        if (paired) {
                            pairing.close()
                            // Opening the screen (or pairing just now) syncs; there's no "Sync now" button.
                            if (role == SyncRole.Client) controller.launchSync(SyncTrigger.Open)
                        } else {
                            pairing.open()
                        }
                    }
            }
        }
    }

    fun onAction(action: SyncAction) {
        when (action) {
            is SyncAction.EditCode -> {
                codeInput.value = action.code.take(MAX_INPUT)
            }

            SyncAction.Pair -> {
                val busy = pairing.state.value.let { it == PairingState.Searching || it == PairingState.Connecting }
                if (busy || !isCompleteCode(codeInput.value)) return
                viewModelScope.launch { pairing.enterCode(codeInput.value) }
            }

            SyncAction.Unpair -> {
                codeInput.value = ""
                viewModelScope.launch { controller.unpair() }
            }

            SyncAction.DismissProblem -> {
                pairing.open()
            }
        }
    }

    override fun onCleared() {
        pairing.close()
    }

    private companion object {
        // Room for dashes or spaces between the groups.
        const val MAX_INPUT = 16
    }
}
