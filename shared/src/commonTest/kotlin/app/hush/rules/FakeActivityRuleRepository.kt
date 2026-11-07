// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.rules

import app.hush.activities.ActivityId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeActivityRuleRepository(
    initial: List<ActivityRule> = emptyList(),
) : ActivityRuleRepository {
    override val rules = MutableStateFlow(initial)

    override suspend fun set(
        appKey: String,
        label: String,
        activity: ActivityId,
    ) = rules.update { all -> all.filter { it.appKey != appKey } + ActivityRule(appKey, label, activity) }

    override suspend fun clear(appKey: String) = rules.update { all -> all.filter { it.appKey != appKey } }
}
