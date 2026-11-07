// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import app.hush.data.db.SyncDao
import kotlin.time.Instant

/** This device's synced data: what it sends, and how it takes in the other device's. */
interface SyncStore {
    /**
     * Every tag, activity and rule (deleted ones too), every finished session and its apps, sent as
     * device [self]. Open detox attempts stay out.
     */
    suspend fun export(self: String): SyncPayload

    /**
     * Merges [remote] in one transaction. Idempotent: applying the same payload again changes
     * nothing. [self] and [now] stamp a tag revived so that at least one tag stays usable.
     */
    suspend fun apply(
        remote: SyncPayload,
        self: String,
        now: Instant,
    ): MergeSummary
}

class RoomSyncStore(
    private val dao: SyncDao,
) : SyncStore {
    override suspend fun export(self: String): SyncPayload = dao.export(self)

    override suspend fun apply(
        remote: SyncPayload,
        self: String,
        now: Instant,
    ): MergeSummary = dao.apply(remote, self, now)
}
