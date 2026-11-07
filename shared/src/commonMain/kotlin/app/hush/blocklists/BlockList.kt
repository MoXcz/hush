// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists

import app.hush.blocker.ProfileId

/** Apps blocked while a session of this list runs. Its id is the blocker profile id. */
data class BlockList(
    val id: ProfileId,
    val name: String,
    val packages: Set<String>,
)

/** What a block list is used for: Pomodoro work phases, or detox path attempts. */
enum class ProfileRole(
    /** The seeded list for this role; the database seeds the same ids. */
    val profileId: ProfileId,
) {
    DeepWork(ProfileId("deep")),
    Detox(ProfileId("detox")),
}
