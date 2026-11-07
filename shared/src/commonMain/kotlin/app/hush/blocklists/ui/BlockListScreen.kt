// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.blocker.BlockableApp
import app.hush.blocker.ProfileId
import app.hush.resources.Res
import app.hush.resources.block_list_back
import app.hush.resources.block_list_no_match
import app.hush.resources.block_list_search
import app.hush.ui.BackButton
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.segmentedColors
import app.hush.ui.theme.segmentedShapes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BlockListRoute(
    id: ProfileId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockListViewModel = koinViewModel(key = id.value) { parametersOf(id.value) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlockListScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

@Composable
fun BlockListScreen(
    state: BlockListUiState,
    onAction: (BlockListAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(
            title = { ScreenTitle(state.name) },
            navigationIcon = {
                BackButton(stringResource(Res.string.block_list_back), onClick = onBack, modifier = Modifier.padding(start = 8.dp))
            },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(BlockListAction.Search(it)) },
            placeholder = { Text(stringResource(Res.string.block_list_search)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceBright,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }

            state.apps.isEmpty() -> {
                Text(
                    stringResource(Res.string.block_list_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }

            else -> {
                AppList(apps = state.apps, selected = state.selected, onToggle = { onAction(BlockListAction.Toggle(it)) })
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<BlockableApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            val checked = app.packageName in selected
            SegmentedListItem(
                checked = checked,
                onCheckedChange = { onToggle(app.packageName) },
                shapes = segmentedShapes(index, apps.size),
                colors = segmentedColors,
                leadingContent = { AppIcon(app) },
                trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
            ) { Text(app.label) }
            if (index < apps.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun AppIcon(
    app: BlockableApp,
    modifier: Modifier = Modifier,
) {
    val icon = app.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(40.dp))
    } else {
        Box(modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Preview
@Composable
private fun BlockListScreenPreview() {
    HushTheme {
        BlockListScreen(
            state =
                BlockListUiState(
                    name = "Deep work",
                    loading = false,
                    apps = listOf(BlockableApp("a", "Browser"), BlockableApp("b", "Mail"), BlockableApp("c", "Video")),
                    selected = setOf("b"),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
