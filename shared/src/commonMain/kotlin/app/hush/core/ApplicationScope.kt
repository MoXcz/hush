// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/** Scope for work that outlives any screen (the timer). One per process, a Koin single. */
class ApplicationScope(
    dispatchers: AppDispatchers,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + dispatchers.default
}
