// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.timer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.activities.ACTIVITY_NAME_MAX_LENGTH
import app.hush.activities.Activity
import app.hush.activities.ActivityRepository
import app.hush.blocklists.BlockListRepository
import app.hush.blocklists.ProfileRole
import app.hush.blocklists.profileFor
import app.hush.core.DeviceFeatures
import app.hush.tags.Tag
import app.hush.tags.TagRepository
import app.hush.timer.Phase
import app.hush.timer.PomodoroEngine
import app.hush.timer.RunState
import app.hush.timer.TimerCommandResult
import app.hush.timer.TimerController
import app.hush.timer.TimerDurations
import app.hush.timer.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    private val controller: TimerController,
    blockLists: BlockListRepository,
    tagRepository: TagRepository,
    private val activityRepository: ActivityRepository,
    features: DeviceFeatures = DeviceFeatures(),
) : ViewModel() {
    private val notice = MutableStateFlow<TimerNotice?>(null)
    private val busy = MutableStateFlow(false)
    private val blockingOff =
        blockLists.lists.map { features.blocking && it.profileFor(ProfileRole.DeepWork) == null }.distinctUntilChanged()
    private val blockingPhoneOnly = !features.blocking

    val uiState: StateFlow<TimerUiState> =
        combine(
            controller.state,
            blockingOff,
            combine(notice, busy, ::Pair),
            tagRepository.tags,
            activityRepository.activities,
        ) { state, blockingOff, (notice, busy), tags, activities ->
            state.toUiState(
                blockingOff = blockingOff,
                blockingPhoneOnly = blockingPhoneOnly,
                notice = notice,
                busy = busy,
                tags = tags,
                activities = activities,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            controller.state.value.toUiState(blockingPhoneOnly = blockingPhoneOnly),
        )

    fun onAction(action: TimerAction) {
        when (action) {
            TimerAction.Start -> command { controller.start() }
            TimerAction.Pause -> command { controller.pause() }
            TimerAction.Skip -> command { controller.skip() }
            TimerAction.Reset -> command { controller.reset() }
            TimerAction.DismissNotice -> notice.value = null
            is TimerAction.SelectTag -> viewModelScope.launch { controller.selectTag(action.id) }
            is TimerAction.SelectActivity -> viewModelScope.launch { controller.selectActivity(action.id) }
            is TimerAction.AddActivity -> addActivity(action.name)
        }
    }

    private fun addActivity(name: String) {
        val trimmed = name.trim().take(ACTIVITY_NAME_MAX_LENGTH)
        if (trimmed.isEmpty()) return
        viewModelScope.launch { controller.selectActivity(activityRepository.add(trimmed)) }
    }

    private fun command(block: suspend () -> TimerCommandResult) {
        if (busy.value) return
        busy.value = true
        notice.value = null
        viewModelScope.launch {
            try {
                notice.value = block().toNotice()
            } finally {
                busy.value = false
            }
        }
    }
}

private fun TimerCommandResult.toNotice(): TimerNotice? =
    when (this) {
        TimerCommandResult.Ok -> null
        TimerCommandResult.NeedsSetup -> TimerNotice.NeedsSetup
        TimerCommandResult.Refused -> TimerNotice.Refused
        TimerCommandResult.CantStopEarly -> TimerNotice.CantStopEarly
        TimerCommandResult.Unreachable -> TimerNotice.Unreachable
        TimerCommandResult.Unconfirmed -> TimerNotice.Unconfirmed
    }

internal fun TimerState.toUiState(
    blockingOff: Boolean = false,
    blockingPhoneOnly: Boolean = false,
    notice: TimerNotice? = null,
    busy: Boolean = false,
    tags: List<Tag> = emptyList(),
    activities: List<Activity> = emptyList(),
): TimerUiState {
    val snapshot = snapshot
    val completed = snapshot.completedWorkPhases
    val next = PomodoroEngine.advance(snapshot, tag?.durations ?: TimerDurations())
    return TimerUiState(
        phase = snapshot.phase,
        remaining = remaining,
        progress = progress(),
        status =
            when (snapshot.run) {
                RunState.Ready -> TimerStatus.Ready
                is RunState.Running -> TimerStatus.Running
                is RunState.Paused -> TimerStatus.Paused
            },
        workPhase = (if (snapshot.phase == Phase.Work) completed + 1 else completed).coerceAtLeast(1),
        workPhasesPerSet = workPhasesPerSet,
        canStopEarly = !(snapshot.blocking && !snapshot.canStopEarly),
        canReset = snapshot.run != RunState.Ready || snapshot.phase != Phase.Work || completed != 0,
        blockingOff = blockingOff,
        blockingPhoneOnly = blockingPhoneOnly,
        notice = notice,
        busy = busy,
        nextPhase = next.phase,
        nextDuration = next.duration,
        tag = tag,
        tags = tags,
        canSwitchTag = !snapshot.workInProgress,
        activity = activity,
        activities = activities,
    )
}

internal fun TimerState.progress(): Float {
    val total = snapshot.duration
    if (total <= kotlin.time.Duration.ZERO) return 0f
    return (1.0 - remaining / total).toFloat().coerceIn(0f, 1f)
}
