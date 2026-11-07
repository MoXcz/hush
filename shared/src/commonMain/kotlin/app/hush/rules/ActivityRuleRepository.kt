// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.rules

import app.hush.activities.ActivityId
import app.hush.data.db.ActivityRuleDao
import app.hush.data.db.ActivityRuleEntity
import app.hush.sync.DeviceIdentity
import app.hush.usage.APP_LABEL_MAX_LENGTH
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** The user's app → activity rules. One rule per app, site or program. */
interface ActivityRuleRepository {
    /** Rules in force, named by the app's label when the rule was written. */
    val rules: Flow<List<ActivityRule>>

    /** Sorts [appKey] into [activity], replacing its rule if it had one. */
    suspend fun set(
        appKey: String,
        label: String,
        activity: ActivityId,
    )

    /** Removes the rule for [appKey], if there is one. */
    suspend fun clear(appKey: String)
}

// Stored by activity uid and cleared with a tombstone, so rules sync like activities.
class RoomActivityRuleRepository(
    private val dao: ActivityRuleDao,
    private val clock: Clock,
    private val identity: DeviceIdentity,
) : ActivityRuleRepository {
    override val rules: Flow<List<ActivityRule>> =
        dao.rules().map { rows -> rows.map { ActivityRule(it.appKey, it.label, it.activityId?.let(::ActivityId)) } }

    override suspend fun set(
        appKey: String,
        label: String,
        activity: ActivityId,
    ) {
        val activityUid = dao.activityUid(activity.value) ?: return
        dao.upsert(
            ActivityRuleEntity(
                appKey = appKey,
                label = label.take(APP_LABEL_MAX_LENGTH),
                activityUid = activityUid,
                deleted = false,
                updatedAtMillis = clock.now().toEpochMilliseconds(),
                updatedBy = identity.deviceId(),
            ),
        )
    }

    override suspend fun clear(appKey: String) {
        dao.softDelete(appKey, clock.now().toEpochMilliseconds(), identity.deviceId())
    }
}
