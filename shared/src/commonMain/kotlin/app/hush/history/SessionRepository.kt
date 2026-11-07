// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history

import app.hush.activities.ActivityId
import app.hush.blocker.ProfileId
import app.hush.data.db.SessionDao
import app.hush.data.db.SessionEntity
import app.hush.tags.TagId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Work phases and detox attempts. Single source of truth for history and path progress. */
interface SessionRepository {
    /** Finished sessions, newest first. */
    val history: Flow<List<Session>>

    /** Planned minutes of the longest passed detox level, or null if none passed yet. */
    val longestPassedLevelMinutes: Flow<Int?>

    /** Records a work phase that has already ended. Returns the new row's uid. */
    suspend fun recordWork(
        profile: ProfileId?,
        tagId: TagId?,
        plannedMinutes: Int,
        startedAt: Instant,
        endedAt: Instant,
        outcome: SessionOutcome,
        activityId: ActivityId? = null,
    ): String

    /** Opens a detox attempt row; it stays open (no end, no outcome) until [finish]. */
    suspend fun startDetoxAttempt(
        profile: ProfileId,
        plannedMinutes: Int,
        startedAt: Instant,
    ): Session

    /** The detox attempt still open, if any. Used to restore it after process death or reboot. */
    suspend fun openDetoxAttempt(): Session?

    suspend fun finish(
        id: SessionId,
        endedAt: Instant,
        outcome: SessionOutcome,
    )
}

class RoomSessionRepository(
    private val dao: SessionDao,
) : SessionRepository {
    override val history: Flow<List<Session>> = dao.finished().map { rows -> rows.map { it.toSession() } }

    override val longestPassedLevelMinutes: Flow<Int?> = dao.longestPassedDetoxMinutes()

    override suspend fun recordWork(
        profile: ProfileId?,
        tagId: TagId?,
        plannedMinutes: Int,
        startedAt: Instant,
        endedAt: Instant,
        outcome: SessionOutcome,
        activityId: ActivityId?,
    ): String {
        val uid = Uuid.random().toString()
        dao.insert(
            SessionEntity(
                type = SessionType.Work.name,
                profileId = profile?.value,
                plannedMinutes = plannedMinutes,
                startedAtMillis = startedAt.toEpochMilliseconds(),
                endedAtMillis = endedAt.toEpochMilliseconds(),
                outcome = outcome.name,
                tagId = tagId?.value,
                uid = uid,
                activityId = activityId?.value,
            ),
        )
        return uid
    }

    override suspend fun startDetoxAttempt(
        profile: ProfileId,
        plannedMinutes: Int,
        startedAt: Instant,
    ): Session {
        val entity =
            SessionEntity(
                type = SessionType.Detox.name,
                profileId = profile.value,
                plannedMinutes = plannedMinutes,
                startedAtMillis = startedAt.toEpochMilliseconds(),
                uid = Uuid.random().toString(),
            )
        return entity.copy(id = dao.insert(entity)).toSession()
    }

    override suspend fun openDetoxAttempt(): Session? = dao.openDetoxAttempt()?.toSession()

    override suspend fun finish(
        id: SessionId,
        endedAt: Instant,
        outcome: SessionOutcome,
    ) {
        dao.finish(id.value, endedAt.toEpochMilliseconds(), outcome.name)
    }
}

private fun SessionEntity.toSession(): Session =
    Session(
        id = SessionId(id),
        type = SessionType.valueOf(type),
        profile = profileId?.let(::ProfileId),
        plannedMinutes = plannedMinutes,
        startedAt = Instant.fromEpochMilliseconds(startedAtMillis),
        endedAt = endedAtMillis?.let(Instant::fromEpochMilliseconds),
        outcome = outcome?.let(SessionOutcome::valueOf),
        tagId = tagId?.let(::TagId),
        activityId = activityId?.let(::ActivityId),
        uid = uid,
    )
