// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists

import app.hush.blocker.ProfileId
import app.hush.data.db.BlockListAppEntity
import app.hush.data.db.BlockListDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** The block lists and their apps. Single source of truth for what a blocker profile blocks. */
interface BlockListRepository {
    /** Every list, in role order (Deep work, Detox). */
    val lists: Flow<List<BlockList>>

    /** Replaces the apps of list [id]. */
    suspend fun setApps(
        id: ProfileId,
        packages: Set<String>,
    )
}

/** The profile for [role], or null while its list has no apps (nothing to block). */
suspend fun BlockListRepository.profileFor(role: ProfileRole): ProfileId? = lists.first().profileFor(role)

fun List<BlockList>.profileFor(role: ProfileRole): ProfileId? = firstOrNull { it.id == role.profileId && it.packages.isNotEmpty() }?.id

class RoomBlockListRepository(
    private val dao: BlockListDao,
) : BlockListRepository {
    override val lists: Flow<List<BlockList>> =
        combine(dao.lists(), dao.apps()) { lists, apps ->
            val byList = apps.groupBy({ it.listId }, { it.packageName })
            lists
                .map { BlockList(ProfileId(it.id), it.name, byList[it.id].orEmpty().toSet()) }
                .sortedBy { list -> ProfileRole.entries.indexOfFirst { it.profileId == list.id } }
        }

    override suspend fun setApps(
        id: ProfileId,
        packages: Set<String>,
    ) = dao.replaceApps(id.value, packages.map { BlockListAppEntity(id.value, it) })
}
