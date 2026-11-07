// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.activities

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeActivityRepository(
    initial: List<Activity> = emptyList(),
) : ActivityRepository {
    val rows = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0) + 1

    override val activities: Flow<List<Activity>> = rows.map { all -> all.filter { !it.deleted } }

    override val allActivities: Flow<List<Activity>> = rows

    override suspend fun add(name: String): ActivityId = ActivityId(nextId++).also { id -> rows.update { it + Activity(id, name) } }

    override suspend fun rename(
        id: ActivityId,
        name: String,
    ) = rows.update { all -> all.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun delete(id: ActivityId) = rows.update { all -> all.map { if (it.id == id) it.copy(deleted = true) else it } }
}
