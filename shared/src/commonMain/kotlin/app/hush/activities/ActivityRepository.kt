// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.activities

import app.hush.data.db.ActivityDao
import app.hush.data.db.ActivityEntity
import app.hush.sync.DeviceIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The user's activities. Single source of truth for what a work phase can be marked as doing. */
interface ActivityRepository {
    /** Activities that can be chosen, oldest first. May be empty. */
    val activities: Flow<List<Activity>>

    /** Every activity ever created, deleted ones included, for history and stats. */
    val allActivities: Flow<List<Activity>>

    suspend fun add(name: String): ActivityId

    suspend fun rename(
        id: ActivityId,
        name: String,
    )

    /** Hides the activity; its sessions keep pointing at it. */
    suspend fun delete(id: ActivityId)
}

/** Every write stamps the time and this device, for the last-writer-wins merge in a sync. */
class RoomActivityRepository(
    private val dao: ActivityDao,
    private val clock: Clock,
    private val identity: DeviceIdentity,
) : ActivityRepository {
    override val activities: Flow<List<Activity>> = dao.all().map { rows -> rows.filter { !it.deleted }.map { it.toActivity() } }

    override val allActivities: Flow<List<Activity>> = dao.all().map { rows -> rows.map { it.toActivity() } }

    override suspend fun add(name: String): ActivityId =
        ActivityId(
            dao.insert(
                ActivityEntity(
                    name = name,
                    uid = Uuid.random().toString(),
                    updatedAtMillis = now(),
                    updatedBy = identity.deviceId(),
                ),
            ),
        )

    override suspend fun rename(
        id: ActivityId,
        name: String,
    ) = dao.rename(id.value, name, now(), identity.deviceId())

    override suspend fun delete(id: ActivityId) = dao.softDelete(id.value, now(), identity.deviceId())

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

private fun ActivityEntity.toActivity(): Activity = Activity(ActivityId(id), name, deleted)
