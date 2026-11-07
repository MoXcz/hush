// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.tags

import app.hush.data.db.TagDao
import app.hush.data.db.TagEntity
import app.hush.sync.DeviceIdentity
import app.hush.timer.TimerDurations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** Tags and their durations. Single source of truth for what a work phase can be tagged with. */
interface TagRepository {
    /** Tags that can be chosen, oldest first. Never empty once the database exists. */
    val tags: Flow<List<Tag>>

    /** Every tag ever created, deleted ones included, for history and stats. */
    val allTags: Flow<List<Tag>>

    suspend fun add(
        name: String,
        color: TagColor,
        durations: TimerDurations,
    ): TagId

    suspend fun update(tag: Tag)

    /** Hides the tag; its sessions keep pointing at it. Refused (false) for the last remaining tag. */
    suspend fun delete(id: TagId): Boolean
}

/** Every write stamps the time and this device, for the last-writer-wins merge in a sync. */
class RoomTagRepository(
    private val dao: TagDao,
    private val clock: Clock,
    private val identity: DeviceIdentity,
) : TagRepository {
    override val tags: Flow<List<Tag>> = dao.all().map { rows -> rows.filter { !it.deleted }.map { it.toTag() } }

    override val allTags: Flow<List<Tag>> = dao.all().map { rows -> rows.map { it.toTag() } }

    override suspend fun add(
        name: String,
        color: TagColor,
        durations: TimerDurations,
    ): TagId =
        TagId(
            dao.insert(
                TagEntity(
                    name = name,
                    color = color.name,
                    workMinutes = durations.work.inWholeMinutes.toInt(),
                    shortBreakMinutes = durations.shortBreak.inWholeMinutes.toInt(),
                    longBreakMinutes = durations.longBreak.inWholeMinutes.toInt(),
                    longBreakAfter = durations.longBreakAfter,
                    uid = Uuid.random().toString(),
                    updatedAtMillis = now(),
                    updatedBy = identity.deviceId(),
                ),
            ),
        )

    override suspend fun update(tag: Tag) =
        dao.updateFields(
            id = tag.id.value,
            name = tag.name,
            color = tag.color.name,
            workMinutes =
                tag.durations.work.inWholeMinutes
                    .toInt(),
            shortBreakMinutes =
                tag.durations.shortBreak.inWholeMinutes
                    .toInt(),
            longBreakMinutes =
                tag.durations.longBreak.inWholeMinutes
                    .toInt(),
            longBreakAfter = tag.durations.longBreakAfter,
            deleted = tag.deleted,
            updatedAtMillis = now(),
            updatedBy = identity.deviceId(),
        )

    override suspend fun delete(id: TagId): Boolean = dao.softDeleteUnlessLast(id.value, now(), identity.deviceId())

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

private fun TagEntity.toTag(): Tag =
    Tag(
        id = TagId(id),
        name = name,
        color = TagColor.entries.firstOrNull { it.name == color } ?: TagColor.Indigo,
        durations =
            TimerDurations(
                work = workMinutes.minutes,
                shortBreak = shortBreakMinutes.minutes,
                longBreak = longBreakMinutes.minutes,
                longBreakAfter = longBreakAfter,
            ),
        deleted = deleted,
    )
