// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.tags

import app.hush.timer.TimerDurations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.minutes

/** The seeded defaults, as the real database starts. */
val defaultTags =
    listOf(
        Tag(TagId(1), "Focus", TagColor.Indigo, TimerDurations()),
        Tag(TagId(2), "Light", TagColor.Teal, TimerDurations(20.minutes, 10.minutes, 20.minutes, 4)),
        Tag(TagId(3), "Deep", TagColor.Plum, TimerDurations(50.minutes, 10.minutes, 30.minutes, 4)),
    )

class FakeTagRepository(
    initial: List<Tag> = defaultTags,
) : TagRepository {
    val rows = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0) + 1

    override val tags: Flow<List<Tag>> = rows.map { all -> all.filter { !it.deleted } }

    override val allTags: Flow<List<Tag>> = rows

    override suspend fun add(
        name: String,
        color: TagColor,
        durations: TimerDurations,
    ): TagId = TagId(nextId++).also { id -> rows.update { it + Tag(id, name, color, durations) } }

    override suspend fun update(tag: Tag) = rows.update { all -> all.map { if (it.id == tag.id) tag else it } }

    override suspend fun delete(id: TagId): Boolean {
        if (rows.value.count { !it.deleted } <= 1) return false
        rows.update { all -> all.map { if (it.id == id) it.copy(deleted = true) else it } }
        return true
    }

    fun setDurations(
        id: TagId,
        durations: TimerDurations,
    ) = rows.update { all -> all.map { if (it.id == id) it.copy(durations = durations) else it } }
}
