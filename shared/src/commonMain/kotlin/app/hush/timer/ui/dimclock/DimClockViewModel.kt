// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui.dimclock

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.settings.SettingsRepository
import app.hush.timer.TimerController
import app.hush.timer.ui.progress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Immutable
data class DimClockUiState(
    val remaining: Duration = Duration.ZERO,
    val progress: Float = 0f,
    /** For the time of day. Changes once a minute, which also drives the burn-in shift. */
    val now: Instant = Instant.DISTANT_PAST,
    val showWhenLocked: Boolean = false,
    /** The phase shown on entry has ended: leave, so the phase-end notification is seen. */
    val exit: Boolean = false,
)

/** UI only: reads the timer, never changes it. */
class DimClockViewModel(
    controller: TimerController,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val entryPhase = controller.state.value.phase

    val uiState: StateFlow<DimClockUiState> =
        combine(
            controller.state,
            minuteTicks(clock),
            settingsRepository.settings.map { it.dimClockShowWhenLocked }.distinctUntilChanged(),
        ) { timer, now, showWhenLocked ->
            DimClockUiState(
                remaining = timer.remaining,
                progress = timer.progress(),
                now = now,
                showWhenLocked = showWhenLocked,
                exit = timer.phase != entryPhase,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DimClockUiState(remaining = controller.state.value.remaining, progress = controller.state.value.progress()),
        )
}

/** Emits now, then at the start of every wall-clock minute. */
internal fun minuteTicks(clock: Clock): Flow<Instant> =
    flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay(MILLIS_PER_MINUTE - now.toEpochMilliseconds().mod(MILLIS_PER_MINUTE))
        }
    }

private const val MILLIS_PER_MINUTE = 60_000L
