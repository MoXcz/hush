// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists

import app.hush.blocker.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Both seeded lists, each with one app unless told otherwise. */
class FakeBlockListRepository(
    deepWork: Set<String> = setOf("com.example.video"),
    detox: Set<String> = setOf("com.example.video"),
) : BlockListRepository {
    override val lists =
        MutableStateFlow(
            listOf(
                BlockList(ProfileRole.DeepWork.profileId, "Deep work", deepWork),
                BlockList(ProfileRole.Detox.profileId, "Detox", detox),
            ),
        )

    override suspend fun setApps(
        id: ProfileId,
        packages: Set<String>,
    ) {
        lists.update { all -> all.map { if (it.id == id) it.copy(packages = packages) else it } }
    }
}
