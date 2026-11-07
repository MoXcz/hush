// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import app.hush.activities.ACTIVITY_NAME_MAX_LENGTH
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.settings.ui.TimerSetting
import app.hush.tags.TAG_NAME_MAX_LENGTH
import app.hush.usage.APP_LABEL_MAX_LENGTH
import app.hush.usage.PACKAGE_NAME_MAX_LENGTH
import kotlin.time.Instant

/** A tag in this database: its local row id (never changes) and its synced fields. */
data class LocalTag(
    val localId: Long,
    val record: TagRecord,
)

/** An activity in this database: its local row id and its synced fields. */
data class LocalActivity(
    val localId: Long,
    val record: ActivityRecord,
)

/** One app row's key: the session's uid and the package. */
data class AppKey(
    val session: String,
    val packageName: String,
)

/** What the merge needs to know about this database. */
data class LocalSyncState(
    val tags: List<LocalTag>,
    /** Every session uid here, open detox attempts included. */
    val sessionUids: Set<String>,
    val activities: List<LocalActivity> = emptyList(),
    val appKeys: Set<AppKey> = emptySet(),
    val rules: List<ActivityRuleRecord> = emptyList(),
)

// Apply in order (tags and activities, sessions, then apps) so every reference is already stored.
data class MergePlan(
    val tagInserts: List<TagRecord>,
    /** Existing rows to overwrite, by local id. */
    val tagUpdates: List<LocalTag>,
    val sessionInserts: List<SessionRecord>,
    /** Remote records that failed validation and were left out. */
    val dropped: Int,
    val activityInserts: List<ActivityRecord> = emptyList(),
    val activityUpdates: List<LocalActivity> = emptyList(),
    val appInserts: List<SessionAppRecord> = emptyList(),
    /** New or newer rules, written over any row with the same app. */
    val ruleUpserts: List<ActivityRuleRecord> = emptyList(),
) {
    val summary: MergeSummary
        get() =
            MergeSummary(
                tagsChanged = tagInserts.size + tagUpdates.size,
                sessionsAdded = sessionInserts.size,
                dropped = dropped,
                activitiesChanged = activityInserts.size + activityUpdates.size,
                appsAdded = appInserts.size,
                rulesChanged = ruleUpserts.size,
            )
}

data class MergeSummary(
    val tagsChanged: Int,
    val sessionsAdded: Int,
    val dropped: Int,
    val activitiesChanged: Int = 0,
    val appsAdded: Int = 0,
    val rulesChanged: Int = 0,
)

/** Sessions and app rows are a union; tags, activities and rules are last-writer-wins by `(updatedAt, updatedBy)`. */
object SyncMerge {
    const val MAX_PLANNED_MINUTES = 1440

    // Caps on free-form strings, so a paired peer can't fill the database with huge values.
    const val MAX_ID_LENGTH = 64
    const val MAX_COLOR_LENGTH = 32

    /** No work phase is longer than a day, so neither is an app's time in one. */
    const val MAX_APP_MILLIS = 24 * 60 * 60 * 1000L

    fun plan(
        local: LocalSyncState,
        remote: SyncPayload,
        self: String,
        now: Instant,
    ): MergePlan {
        val validTags = remote.tags.filter { it.isValid() }
        val validSessions = remote.sessions.filter { it.isValid() }
        val validActivities = remote.activities.filter { it.isValid() }
        val validApps = remote.apps.filter { it.isValid() }
        val validRules = remote.activityRules.filter { it.isValid() }
        val dropped =
            (remote.tags.size - validTags.size) + (remote.sessions.size - validSessions.size) +
                (remote.activities.size - validActivities.size) + (remote.apps.size - validApps.size) +
                (remote.activityRules.size - validRules.size)

        val localByUid = local.tags.associateBy { it.record.uid }
        val inserts = linkedMapOf<String, TagRecord>()
        val updates = linkedMapOf<String, LocalTag>()
        // A payload should hold each uid once; if not, its newest stamp counts.
        validTags.groupBy { it.uid }.values.map { same -> same.maxWith(stampOrder) }.forEach { incoming ->
            val existing = localByUid[incoming.uid]
            when {
                existing == null -> inserts[incoming.uid] = incoming
                stampOrder.compare(incoming, existing.record) > 0 -> updates[incoming.uid] = existing.copy(record = incoming)
            }
        }

        val result = local.tags.map { updates[it.record.uid]?.record ?: it.record } + inserts.values
        if (result.isNotEmpty() && result.none { !it.deleted }) {
            val revived = result.maxWith(stampOrder).copy(deleted = false, updatedAt = now.toEpochMilliseconds(), updatedBy = self)
            val existing = localByUid[revived.uid]
            if (existing == null) inserts[revived.uid] = revived else updates[revived.uid] = existing.copy(record = revived)
        }

        val activityByUid = local.activities.associateBy { it.record.uid }
        val activityInserts = linkedMapOf<String, ActivityRecord>()
        val activityUpdates = linkedMapOf<String, LocalActivity>()
        validActivities.groupBy { it.uid }.values.map { same -> same.maxWith(activityStampOrder) }.forEach { incoming ->
            val existing = activityByUid[incoming.uid]
            when {
                existing == null -> {
                    activityInserts[incoming.uid] = incoming
                }

                activityStampOrder.compare(incoming, existing.record) > 0 -> {
                    activityUpdates[incoming.uid] =
                        existing.copy(record = incoming)
                }
            }
        }

        val knownTags = localByUid.keys + inserts.keys
        val knownActivities = activityByUid.keys + activityInserts.keys
        val sessions =
            validSessions
                .distinctBy { it.uid }
                .filter { it.uid !in local.sessionUids }
                .map { if (it.tag != null && it.tag !in knownTags) it.copy(tag = null) else it }
                .map { if (it.activity != null && it.activity !in knownActivities) it.copy(activity = null) else it }
        // App rows only for sessions this database will have; a row for an unknown session waits for a later sync.
        val knownSessions = local.sessionUids + sessions.map { it.uid }
        val apps =
            validApps
                .distinctBy { AppKey(it.session, it.packageName) }
                .filter { it.session in knownSessions && AppKey(it.session, it.packageName) !in local.appKeys }
                .map { if (it.label.isBlank()) it.copy(label = it.packageName.take(APP_LABEL_MAX_LENGTH)) else it }
        // A rule whose activity isn't known is kept: it sorts nothing until the activity arrives.
        val localRules = local.rules.associateBy { it.app }
        val rules =
            validRules
                .groupBy { it.app }
                .values
                .map { same -> same.maxWith(ruleStampOrder) }
                .filter { incoming -> localRules[incoming.app]?.let { ruleStampOrder.compare(incoming, it) > 0 } ?: true }
                .map { if (it.label.isBlank()) it.copy(label = it.app.take(APP_LABEL_MAX_LENGTH)) else it }
        return MergePlan(
            tagInserts = inserts.values.toList(),
            tagUpdates = updates.values.toList(),
            sessionInserts = sessions,
            dropped = dropped,
            activityInserts = activityInserts.values.toList(),
            activityUpdates = activityUpdates.values.toList(),
            appInserts = apps,
            ruleUpserts = rules,
        )
    }

    private val stampOrder = compareBy<TagRecord>({ it.updatedAt }, { it.updatedBy })
    private val activityStampOrder = compareBy<ActivityRecord>({ it.updatedAt }, { it.updatedBy })
    private val ruleStampOrder = compareBy<ActivityRuleRecord>({ it.updatedAt }, { it.updatedBy })

    private fun ActivityRuleRecord.isValid(): Boolean =
        app.isNotBlank() &&
            app.length <= PACKAGE_NAME_MAX_LENGTH &&
            label.length <= APP_LABEL_MAX_LENGTH &&
            activity.isNotBlank() &&
            activity.length <= MAX_ID_LENGTH &&
            updatedBy.length <= MAX_ID_LENGTH

    private fun ActivityRecord.isValid(): Boolean =
        uid.isNotBlank() &&
            uid.length <= MAX_ID_LENGTH &&
            name.isNotBlank() &&
            name.length <= ACTIVITY_NAME_MAX_LENGTH &&
            updatedBy.length <= MAX_ID_LENGTH

    private fun SessionAppRecord.isValid(): Boolean =
        session.isNotBlank() &&
            session.length <= MAX_ID_LENGTH &&
            packageName.isNotBlank() &&
            packageName.length <= PACKAGE_NAME_MAX_LENGTH &&
            label.length <= APP_LABEL_MAX_LENGTH &&
            millis in 1..MAX_APP_MILLIS

    private fun TagRecord.isValid(): Boolean =
        uid.isNotBlank() &&
            uid.length <= MAX_ID_LENGTH &&
            name.isNotBlank() &&
            name.length <= TAG_NAME_MAX_LENGTH &&
            work in TimerSetting.Work.range &&
            shortBreak in TimerSetting.ShortBreak.range &&
            longBreak in TimerSetting.LongBreak.range &&
            longBreakAfter in TimerSetting.LongBreakAfter.range &&
            color.length <= MAX_COLOR_LENGTH &&
            updatedBy.length <= MAX_ID_LENGTH

    private fun SessionRecord.isValid(): Boolean {
        val end = endedAt ?: return false
        return uid.isNotBlank() &&
            uid.length <= MAX_ID_LENGTH &&
            (profile?.length ?: 0) <= MAX_ID_LENGTH &&
            (tag?.length ?: 0) <= MAX_ID_LENGTH &&
            (activity?.length ?: 0) <= MAX_ID_LENGTH &&
            SessionType.entries.any { it.name == type } &&
            SessionOutcome.entries.any { it.name == outcome } &&
            plannedMinutes in 1..MAX_PLANNED_MINUTES &&
            end >= startedAt
    }
}
