// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.history.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import app.hush.detox.ui.levelLabel
import app.hush.history.SessionOutcome
import app.hush.history.SessionType
import app.hush.resources.Res
import app.hush.resources.history_abandoned
import app.hush.resources.history_completed
import app.hush.resources.history_detox
import app.hush.resources.history_passed
import app.hush.resources.history_stopped
import app.hush.resources.history_tagged
import app.hush.resources.history_work
import app.hush.tags.Tag
import app.hush.tags.ui.TagDot
import app.hush.tags.ui.tagName
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Immutable
data class HistoryItem(
    val type: SessionType,
    val plannedMinutes: Int,
    val startedAt: Instant,
    val outcome: SessionOutcome,
    /** The work phase's tag; null for detox and for a tag that can't be found. */
    val tag: Tag? = null,
)

@Composable
internal fun HistoryRow(
    item: HistoryItem,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val length = levelLabel(item.plannedMinutes)
    val title =
        when (item.type) {
            SessionType.Work -> {
                item.tag?.let { stringResource(Res.string.history_tagged, tagName(it), length) }
                    ?: stringResource(Res.string.history_work, length)
            }

            SessionType.Detox -> {
                stringResource(Res.string.history_detox, length)
            }
        }
    val outcome =
        when (item.outcome) {
            SessionOutcome.Completed -> if (item.type == SessionType.Detox) Res.string.history_passed else Res.string.history_completed
            SessionOutcome.Stopped -> Res.string.history_stopped
            SessionOutcome.Abandoned -> Res.string.history_abandoned
        }
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clip(shape),
        colors = ListItemDefaults.colors(containerColor = scheme.surfaceBright),
        leadingContent = item.tag?.let { tag -> { TagDot(tag.color) } },
        headlineContent = { Text(title) },
        supportingContent = { Text(formatDateTime(item.startedAt)) },
        trailingContent = {
            Text(
                stringResource(outcome),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.outcome == SessionOutcome.Completed) scheme.primary else scheme.onSurfaceVariant,
            )
        },
    )
}
