// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.rules

import app.hush.activities.ActivityId

/**
 * Time on the app stored under [appKey] (a package, a desktop window class, `web:<site>` or
 * `term:<program>`) counts for [activityId] in Stats' "By activity"; [activityId] is null when the
 * activity isn't in this database.
 */
data class ActivityRule(
    val appKey: String,
    val label: String,
    val activityId: ActivityId?,
)
