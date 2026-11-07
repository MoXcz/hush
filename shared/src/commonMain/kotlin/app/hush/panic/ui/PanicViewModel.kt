// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.panic.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.detox.DetoxCommandResult
import app.hush.detox.DetoxController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 5-4-3-2-1 senses, one prompt per fifth of the exercise. */
enum class GroundingStep(
    val count: Int,
) {
    See(5),
    Touch(4),
    Hear(3),
    Smell(2),
    Taste(1),
}

enum class PanicNotice { CantStopEarly, Unreachable, NeedsSetup }

@Immutable
data class PanicUiState(
    val remaining: Duration = PanicViewModel.EXERCISE,
    val step: GroundingStep = GroundingStep.See,
    /** The exercise is over: the choice between going back and ending is shown. */
    val done: Boolean = false,
    /** False when the detox profile can't stop early: "End session" is replaced by an explanation. */
    val canStopEarly: Boolean = true,
    /** No attempt is running any more (ended here, or passed meanwhile): the screen closes. */
    val closed: Boolean = false,
    val notice: PanicNotice? = null,
    val busy: Boolean = false,
)

sealed interface PanicAction {
    data object EndSession : PanicAction

    data object DismissNotice : PanicAction
}

/**
 * The panic flow: a 60-second grounding exercise before the user may end a detox attempt. "Back
 * to focus" is always there (it's just navigating back); "End session" only after the exercise.
 */
class PanicViewModel(
    private val controller: DetoxController,
    clock: Clock,
) : ViewModel() {
    private val startedAt = clock.now()
    private val elapsed = MutableStateFlow(Duration.ZERO)
    private val notice = MutableStateFlow<PanicNotice?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<PanicUiState> =
        combine(elapsed, controller.state, notice, busy) { elapsed, detox, notice, busy ->
            val remaining = (EXERCISE - elapsed).coerceAtLeast(Duration.ZERO)
            val stepLength = EXERCISE / GroundingStep.entries.size
            PanicUiState(
                remaining = remaining,
                step = GroundingStep.entries[(elapsed / stepLength).toInt().coerceIn(0, GroundingStep.entries.lastIndex)],
                done = remaining == Duration.ZERO,
                canStopEarly = detox.attempt?.canStopEarly ?: true,
                closed = detox.attempt == null,
                notice = notice,
                busy = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanicUiState())

    init {
        viewModelScope.launch {
            while (elapsed.value < EXERCISE) {
                delay(1.seconds)
                elapsed.value = (clock.now() - startedAt).coerceIn(Duration.ZERO, EXERCISE)
            }
        }
    }

    fun onAction(action: PanicAction) {
        when (action) {
            PanicAction.EndSession -> endSession()
            PanicAction.DismissNotice -> notice.value = null
        }
    }

    private fun endSession() {
        if (busy.value || elapsed.value < EXERCISE) return
        busy.value = true
        notice.value = null
        viewModelScope.launch {
            try {
                notice.value =
                    when (controller.endEarly()) {
                        DetoxCommandResult.Ok -> null

                        DetoxCommandResult.CantStopEarly -> PanicNotice.CantStopEarly

                        DetoxCommandResult.NeedsSetup -> PanicNotice.NeedsSetup

                        DetoxCommandResult.Unreachable,
                        DetoxCommandResult.Refused,
                        DetoxCommandResult.NoProfile,
                        DetoxCommandResult.LevelLocked,
                        DetoxCommandResult.Unconfirmed,
                        -> PanicNotice.Unreachable
                    }
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val EXERCISE: Duration = 60.seconds
    }
}
