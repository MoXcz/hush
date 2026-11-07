// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.tags.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hush.resources.Res
import app.hush.resources.tag_deleted
import app.hush.resources.tag_summary
import app.hush.tags.Tag
import app.hush.tags.TagColor
import app.hush.ui.theme.color
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TagDot(
    color: TagColor,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Box(modifier.size(size).background(color.color(), CircleShape))
}

/** "25 / 5 / 15 min · long break after 4". */
@Composable
internal fun tagSummary(tag: Tag): String =
    stringResource(
        Res.string.tag_summary,
        tag.durations.work.inWholeMinutes,
        tag.durations.shortBreak.inWholeMinutes,
        tag.durations.longBreak.inWholeMinutes,
        tag.durations.longBreakAfter,
    )

/** The tag's name, marked when it was deleted (history and stats keep showing it). */
@Composable
internal fun tagName(tag: Tag): String = if (tag.deleted) stringResource(Res.string.tag_deleted, tag.name) else tag.name
