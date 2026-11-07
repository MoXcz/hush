// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.blocklists.BlockListRepository
import app.hush.blocklists.ProfileRole
import app.hush.blocklists.profileFor
import app.hush.core.DeviceFeatures
import app.hush.detox.DetoxCommandResult
import app.hush.detox.DetoxController
import app.hush.detox.DetoxPath
import app.hush.detox.DetoxState
import app.hush.history.SessionRepository
import app.hush.settings.Settings
import app.hush.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PathViewModel(
    private val controller: DetoxController,
    settingsRepository: SettingsRepository,
    blockLists: BlockListRepository,
    sessions: SessionRepository,
    private val features: DeviceFeatures = DeviceFeatures(),
) : ViewModel() {
    private val notice = MutableStateFlow<PathNotice?>(null)
    private val busy = MutableStateFlow(false)

    // Kept in memory only: the next level is offered again after an attempt.
    private val selected = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<PathUiState> =
        combine(
            controller.state,
            combine(settingsRepository.settings, blockLists.lists) { settings, lists ->
                settings to
                    (lists.profileFor(ProfileRole.Detox) == null)
            },
            sessions.longestPassedLevelMinutes,
            combine(notice, busy, ::Pair),
            selected,
        ) { detox, (settings, noProfile), longest, (notice, busy), selected ->
            toUiState(
                detox,
                settings,
                longest,
                notice,
                busy,
                phoneOnly = !features.blocking,
                noProfile = noProfile,
                selectedMinutes = selected,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PathUiState())

    fun onAction(action: PathAction) {
        when (action) {
            PathAction.StartAttempt -> start()
            is PathAction.SelectLevel -> select(action.minutes)
            PathAction.Check -> viewModelScope.launch { controller.check() }
            PathAction.DismissOutcome -> controller.acknowledgeOutcome()
            PathAction.DismissNotice -> notice.value = null
        }
    }

    private fun select(minutes: Int) {
        if (!features.blocking || busy.value || controller.state.value.attempt != null) return
        selected.value = minutes
    }

    private fun start() {
        if (busy.value) return
        busy.value = true
        notice.value = null
        val level = uiState.value.chosenLevelMinutes?.minutes
        viewModelScope.launch {
            try {
                val result = controller.startAttempt(level)
                // A choice is for one attempt; a failed start keeps it for the retry.
                if (result in CHOICE_USED) selected.value = null
                notice.value =
                    when (result) {
                        DetoxCommandResult.Ok, DetoxCommandResult.NoProfile, DetoxCommandResult.CantStopEarly -> null

                        // The goal changed under the choice: the next level is offered again.
                        DetoxCommandResult.LevelLocked -> null

                        DetoxCommandResult.NeedsSetup -> PathNotice.NeedsSetup

                        DetoxCommandResult.Refused -> PathNotice.Refused

                        DetoxCommandResult.Unreachable -> PathNotice.Unreachable

                        DetoxCommandResult.Unconfirmed -> PathNotice.Unconfirmed
                    }
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        val CHOICE_USED = setOf(DetoxCommandResult.Ok, DetoxCommandResult.Unconfirmed, DetoxCommandResult.LevelLocked)
    }
}

internal fun toUiState(
    detox: DetoxState,
    settings: Settings,
    longestPassedMinutes: Int?,
    notice: PathNotice? = null,
    busy: Boolean = false,
    phoneOnly: Boolean = false,
    noProfile: Boolean = false,
    selectedMinutes: Int? = null,
): PathUiState {
    val progress = DetoxPath.progress(settings.detoxGoal, longestPassedMinutes?.minutes)
    val attempt = detox.attempt
    val canChoose = attempt == null && !phoneOnly
    // A choice that is the next level, or no longer choosable (the goal changed), means the default.
    val chosen =
        selectedMinutes?.minutes?.takeIf { canChoose && it != progress.currentLevel && it in progress.choosable }
    // A running attempt keeps the level it started with, even if the goal changed since.
    val current = attempt?.levelMinutes?.minutes ?: chosen ?: progress.currentLevel
    val levels =
        progress.levels.map { level ->
            val status =
                when {
                    level == progress.currentLevel && !progress.complete -> LevelStatus.Current
                    longestPassedMinutes != null && level.inWholeMinutes <= longestPassedMinutes -> LevelStatus.Passed
                    else -> LevelStatus.Locked
                }
            LevelItem(
                minutes = level.inWholeMinutes.toInt(),
                status = status,
                selected = level == current,
                selectable = canChoose && level in progress.choosable,
            )
        }
    return PathUiState(
        levels = levels,
        currentLevelMinutes = current.inWholeMinutes.toInt(),
        chosenLevelMinutes = chosen?.inWholeMinutes?.toInt(),
        complete = progress.complete,
        active =
            attempt?.let {
                val total = it.levelMinutes.minutes
                ActiveAttemptUi(
                    levelMinutes = it.levelMinutes,
                    remaining = detox.remaining,
                    progress = if (total > Duration.ZERO) (1.0 - detox.remaining / total).toFloat().coerceIn(0f, 1f) else 0f,
                    canStopEarly = it.canStopEarly,
                )
            },
        outcome = detox.lastOutcome,
        noProfile = noProfile,
        notice = notice,
        busy = busy,
        phoneOnly = phoneOnly,
    )
}
