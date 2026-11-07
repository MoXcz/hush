// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import app.hush.blocker.ProfileId
import kotlin.time.Instant

/** What the service enforces right now: the running block with its list's current apps. */
data class ActiveBlock(
    val profile: ProfileId,
    val endsAt: Instant,
    val packages: Set<String>,
)

internal object BlockRules {
    /** True if [packageName] coming to the front must be covered by the block screen. */
    fun shouldBlock(
        packageName: String,
        block: ActiveBlock?,
        now: Instant,
        alwaysAllowed: Set<String>,
    ): Boolean = block != null && now < block.endsAt && packageName in block.packages && packageName !in alwaysAllowed
}
