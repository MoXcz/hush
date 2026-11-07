// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history

import app.hush.activities.ActivityId
import app.hush.blocker.ProfileId
import app.hush.tags.TagId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeSessionRepository(
    initial: List<Session> = emptyList(),
) : SessionRepository {
    val rows = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0) + 1

    override val history: Flow<List<Session>> =
        rows.map { all -> all.filter { it.endedAt != null }.sortedByDescending { it.startedAt } }

    override val longestPassedLevelMinutes: Flow<Int?> =
        rows.map { all ->
            all
                .filter { it.type == SessionType.Detox && it.outcome == SessionOutcome.Completed }
                .maxOfOrNull { it.plannedMinutes }
        }

    override suspend fun recordWork(
        profile: ProfileId?,
        tagId: TagId?,
        plannedMinutes: Int,
        startedAt: Instant,
        endedAt: Instant,
        outcome: SessionOutcome,
        activityId: ActivityId?,
    ): String {
        val id = SessionId(nextId++)
        add(Session(id, SessionType.Work, profile, plannedMinutes, startedAt, endedAt, outcome, tagId, activityId, uidOf(id)))
        return uidOf(id)
    }

    fun uidOf(id: SessionId): String = "session-${id.value}"

    override suspend fun startDetoxAttempt(
        profile: ProfileId,
        plannedMinutes: Int,
        startedAt: Instant,
    ): Session = Session(SessionId(nextId++), SessionType.Detox, profile, plannedMinutes, startedAt).also(::add)

    override suspend fun openDetoxAttempt(): Session? = rows.value.lastOrNull { it.type == SessionType.Detox && it.endedAt == null }

    override suspend fun finish(
        id: SessionId,
        endedAt: Instant,
        outcome: SessionOutcome,
    ) {
        rows.update { all -> all.map { if (it.id == id && it.endedAt == null) it.copy(endedAt = endedAt, outcome = outcome) else it } }
    }

    private fun add(session: Session) = rows.update { it + session }
}
