// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hush.blocker.ProfileId
import app.hush.blocklists.BlockList
import app.hush.blocklists.ProfileRole
import app.hush.resources.Res
import app.hush.resources.block_list_apps
import app.hush.resources.block_list_for_path
import app.hush.resources.block_list_for_timer
import app.hush.resources.block_list_no_apps
import app.hush.resources.ic_keyboard_arrow_right
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One row per block list (name, what it's for, how many apps), opening its app picker. */
@Composable
internal fun BlockListRows(
    lists: List<BlockList>,
    onOpen: (ProfileId) -> Unit,
    modifier: Modifier = Modifier,
    firstIndex: Int = 0,
    count: Int = lists.size,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lists.forEachIndexed { index, list ->
            SegmentedListItem(
                onClick = { onOpen(list.id) },
                shapes = segmentedShapes(firstIndex + index, count),
                colors = segmentedColors,
                supportingContent = { Text(blockListSummary(list)) },
                trailingContent = { Icon(painterResource(Res.drawable.ic_keyboard_arrow_right), contentDescription = null) },
            ) { Text(list.name) }
        }
    }
}

@Composable
private fun blockListSummary(list: BlockList): String {
    val apps =
        if (list.packages.isEmpty()) {
            stringResource(Res.string.block_list_no_apps)
        } else {
            pluralStringResource(Res.plurals.block_list_apps, list.packages.size, list.packages.size)
        }
    val use =
        when (ProfileRole.entries.firstOrNull { it.profileId == list.id }) {
            ProfileRole.DeepWork -> stringResource(Res.string.block_list_for_timer)
            ProfileRole.Detox -> stringResource(Res.string.block_list_for_path)
            null -> return apps
        }
    return "$apps · $use"
}
