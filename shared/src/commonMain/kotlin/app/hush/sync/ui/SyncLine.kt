// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.ui

import androidx.compose.runtime.Composable
import app.hush.resources.Res
import app.hush.resources.sync_line_days
import app.hush.resources.sync_line_failed
import app.hush.resources.sync_line_hours
import app.hush.resources.sync_line_just_now
import app.hush.resources.sync_line_minutes
import app.hush.resources.sync_line_never
import app.hush.resources.sync_line_not_found
import app.hush.resources.sync_line_rejected
import app.hush.resources.sync_line_syncing
import app.hush.resources.sync_line_too_new
import app.hush.sync.SyncOutcome
import app.hush.sync.SyncStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The one line under the peer's name: when it last synced, or why it didn't. */
sealed interface SyncLine {
    data object Never : SyncLine

    data object Syncing : SyncLine

    data class Synced(
        val minutesAgo: Long,
    ) : SyncLine

    data object NotFound : SyncLine

    data object Rejected : SyncLine

    data object TooNew : SyncLine

    data object Failed : SyncLine
}

fun syncLine(
    status: SyncStatus.Paired,
    now: Instant,
): SyncLine {
    if (status.syncing) return SyncLine.Syncing
    val synced = status.lastSyncedAt?.let { SyncLine.Synced((now - it).inWholeMinutes.coerceAtLeast(0)) }
    return when (status.lastResult) {
        null, SyncOutcome.Synced -> synced ?: SyncLine.Never
        SyncOutcome.NotFound -> SyncLine.NotFound
        SyncOutcome.Rejected -> SyncLine.Rejected
        SyncOutcome.TooNew -> SyncLine.TooNew
        SyncOutcome.Failed -> SyncLine.Failed
    }
}

@Composable
internal fun syncLineText(line: SyncLine): String =
    when (line) {
        SyncLine.Never -> {
            stringResource(Res.string.sync_line_never)
        }

        SyncLine.Syncing -> {
            stringResource(Res.string.sync_line_syncing)
        }

        is SyncLine.Synced -> {
            when {
                line.minutesAgo < 1 -> {
                    stringResource(Res.string.sync_line_just_now)
                }

                line.minutesAgo < 60 -> {
                    stringResource(Res.string.sync_line_minutes, line.minutesAgo.toInt())
                }

                line.minutesAgo < 24 * 60 -> {
                    (line.minutesAgo / 60).toInt().let {
                        pluralStringResource(
                            Res.plurals.sync_line_hours,
                            it,
                            it,
                        )
                    }
                }

                else -> {
                    (line.minutesAgo / (24 * 60)).toInt().let { pluralStringResource(Res.plurals.sync_line_days, it, it) }
                }
            }
        }

        SyncLine.NotFound -> {
            stringResource(Res.string.sync_line_not_found)
        }

        SyncLine.Rejected -> {
            stringResource(Res.string.sync_line_rejected)
        }

        SyncLine.TooNew -> {
            stringResource(Res.string.sync_line_too_new)
        }

        SyncLine.Failed -> {
            stringResource(Res.string.sync_line_failed)
        }
    }
