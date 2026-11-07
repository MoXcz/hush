// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import app.hush.sync.ActivityRecord
import app.hush.sync.ActivityRuleRecord
import app.hush.sync.AppKey
import app.hush.sync.LocalActivity
import app.hush.sync.LocalSyncState
import app.hush.sync.LocalTag
import app.hush.sync.MergeSummary
import app.hush.sync.SessionAppRecord
import app.hush.sync.SessionRecord
import app.hush.sync.SyncMerge
import app.hush.sync.SyncPayload
import app.hush.sync.TagRecord
import kotlin.time.Instant

@Dao
interface SyncDao {
    @Query("SELECT * FROM tag ORDER BY uid")
    suspend fun tags(): List<TagEntity>

    @Query("SELECT * FROM session WHERE endedAtMillis IS NOT NULL ORDER BY uid")
    suspend fun finishedSessions(): List<SessionEntity>

    @Query("SELECT uid FROM session")
    suspend fun sessionUids(): List<String>

    @Query("SELECT * FROM activity ORDER BY uid")
    suspend fun activities(): List<ActivityEntity>

    @Query("SELECT * FROM session_app ORDER BY sessionUid, packageName")
    suspend fun sessionApps(): List<SessionAppEntity>

    @Query("SELECT * FROM activity_rule ORDER BY appKey")
    suspend fun rules(): List<ActivityRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<ActivityRuleEntity>)

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Insert
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Query(
        "UPDATE activity SET name = :name, deleted = :deleted, updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy " +
            "WHERE id = :id",
    )
    suspend fun updateActivity(
        id: Long,
        name: String,
        deleted: Boolean,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionApps(apps: List<SessionAppEntity>)

    @Query(
        "UPDATE tag SET name = :name, color = :color, workMinutes = :workMinutes, shortBreakMinutes = :shortBreakMinutes, " +
            "longBreakMinutes = :longBreakMinutes, longBreakAfter = :longBreakAfter, deleted = :deleted, " +
            "updatedAtMillis = :updatedAtMillis, updatedBy = :updatedBy WHERE id = :id",
    )
    suspend fun updateTag(
        id: Long,
        name: String,
        color: String,
        workMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        longBreakAfter: Int,
        deleted: Boolean,
        updatedAtMillis: Long,
        updatedBy: String,
    )

    // A uid already here (even an open attempt) is never overwritten.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    /** Reads, merges and writes in one transaction, so a concurrent local edit can't slip in between. */
    @Transaction
    suspend fun apply(
        remote: SyncPayload,
        self: String,
        now: Instant,
    ): MergeSummary {
        val local =
            LocalSyncState(
                tags = tags().map { LocalTag(it.id, it.toRecord()) },
                sessionUids = sessionUids().toSet(),
                activities = activities().map { LocalActivity(it.id, it.toRecord()) },
                appKeys = sessionApps().map { AppKey(it.sessionUid, it.packageName) }.toSet(),
                rules = rules().map { it.toRecord() },
            )
        val plan = SyncMerge.plan(local, remote, self, now)
        plan.tagInserts.forEach { insertTag(it.toEntity()) }
        plan.tagUpdates.forEach { (id, r) ->
            updateTag(id, r.name, r.color, r.work, r.shortBreak, r.longBreak, r.longBreakAfter, r.deleted, r.updatedAt, r.updatedBy)
        }
        plan.activityInserts.forEach { insertActivity(it.toEntity()) }
        plan.activityUpdates.forEach { (id, r) -> updateActivity(id, r.name, r.deleted, r.updatedAt, r.updatedBy) }
        if (plan.sessionInserts.isNotEmpty()) {
            val tagIds = tags().associate { it.uid to it.id }
            val activityIds = activities().associate { it.uid to it.id }
            insertSessions(plan.sessionInserts.map { it.toEntity(it.tag?.let(tagIds::get), it.activity?.let(activityIds::get)) })
        }
        if (plan.appInserts.isNotEmpty()) insertSessionApps(plan.appInserts.map { it.toEntity() })
        if (plan.ruleUpserts.isNotEmpty()) upsertRules(plan.ruleUpserts.map { it.toEntity() })
        return plan.summary
    }

    @Transaction
    suspend fun export(self: String): SyncPayload {
        val tags = tags()
        val tagUids = tags.associate { it.id to it.uid }
        val activities = activities()
        val activityUids = activities.associate { it.id to it.uid }
        return SyncPayload(
            device = self,
            tags = tags.map { it.toRecord() },
            sessions = finishedSessions().map { it.toRecord(it.tagId?.let(tagUids::get), it.activityId?.let(activityUids::get)) },
            activities = activities.map { it.toRecord() },
            apps = sessionApps().map { it.toRecord() },
            activityRules = rules().map { it.toRecord() },
        )
    }
}

private fun TagEntity.toRecord(): TagRecord =
    TagRecord(
        uid = uid,
        name = name,
        color = color,
        work = workMinutes,
        shortBreak = shortBreakMinutes,
        longBreak = longBreakMinutes,
        longBreakAfter = longBreakAfter,
        deleted = deleted,
        updatedAt = updatedAtMillis,
        updatedBy = updatedBy,
    )

private fun TagRecord.toEntity(): TagEntity =
    TagEntity(
        name = name,
        color = color,
        workMinutes = work,
        shortBreakMinutes = shortBreak,
        longBreakMinutes = longBreak,
        longBreakAfter = longBreakAfter,
        deleted = deleted,
        uid = uid,
        updatedAtMillis = updatedAt,
        updatedBy = updatedBy,
    )

private fun ActivityEntity.toRecord(): ActivityRecord = ActivityRecord(uid, name, deleted, updatedAtMillis, updatedBy)

private fun ActivityRecord.toEntity(): ActivityEntity =
    ActivityEntity(name = name, deleted = deleted, uid = uid, updatedAtMillis = updatedAt, updatedBy = updatedBy)

private fun ActivityRuleEntity.toRecord(): ActivityRuleRecord =
    ActivityRuleRecord(appKey, label, activityUid, deleted, updatedAtMillis, updatedBy)

private fun ActivityRuleRecord.toEntity(): ActivityRuleEntity = ActivityRuleEntity(app, label, activity, deleted, updatedAt, updatedBy)

private fun SessionAppEntity.toRecord(): SessionAppRecord = SessionAppRecord(sessionUid, packageName, label, foregroundMillis)

private fun SessionAppRecord.toEntity(): SessionAppEntity = SessionAppEntity(session, packageName, label, millis)

private fun SessionEntity.toRecord(
    tagUid: String?,
    activityUid: String?,
): SessionRecord =
    SessionRecord(
        uid = uid,
        type = type,
        profile = profileId,
        plannedMinutes = plannedMinutes,
        startedAt = startedAtMillis,
        endedAt = endedAtMillis,
        outcome = outcome.orEmpty(),
        tag = tagUid,
        activity = activityUid,
    )

private fun SessionRecord.toEntity(
    tagId: Long?,
    activityId: Long?,
): SessionEntity =
    SessionEntity(
        type = type,
        profileId = profile,
        plannedMinutes = plannedMinutes,
        startedAtMillis = startedAt,
        endedAtMillis = endedAt,
        outcome = outcome,
        tagId = tagId,
        uid = uid,
        activityId = activityId,
    )
