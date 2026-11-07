// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hush.resources.Res
import app.hush.resources.sync_back
import app.hush.resources.sync_client_instructions
import app.hush.resources.sync_client_paired_hint
import app.hush.resources.sync_code_expires
import app.hush.resources.sync_code_hint
import app.hush.resources.sync_code_label
import app.hush.resources.sync_code_renewing
import app.hush.resources.sync_connecting
import app.hush.resources.sync_dismiss
import app.hush.resources.sync_host_instructions
import app.hush.resources.sync_host_paired_hint
import app.hush.resources.sync_not_discoverable
import app.hush.resources.sync_pair
import app.hush.resources.sync_problem_error
import app.hush.resources.sync_problem_expired
import app.hush.resources.sync_problem_needs_permission
import app.hush.resources.sync_problem_not_found
import app.hush.resources.sync_problem_too_new
import app.hush.resources.sync_problem_wrong_code
import app.hush.resources.sync_same_network
import app.hush.resources.sync_searching
import app.hush.resources.sync_title
import app.hush.resources.sync_unpair
import app.hush.sync.PairingFailure
import app.hush.sync.SyncRole
import app.hush.ui.AnimatedNotice
import app.hush.ui.BackButton
import app.hush.ui.ScreenTitle
import app.hush.ui.ScreenTopBar
import app.hush.ui.theme.HushTheme
import app.hush.ui.theme.segmentShape
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SyncRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncScreen(state = state, onAction = viewModel::onAction, onBack = onBack, modifier = modifier)
}

@Composable
fun SyncScreen(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Background first, so it also fills the space the caller pads out for the bottom bar.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        ScreenTopBar(
            title = { ScreenTitle(stringResource(Res.string.sync_title)) },
            navigationIcon = {
                BackButton(stringResource(Res.string.sync_back), onClick = onBack, modifier = Modifier.padding(start = 8.dp))
            },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.discoverable && !state.loading) {
                Text(
                    stringResource(Res.string.sync_not_discoverable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp),
                )
            }
            when {
                state.loading -> Unit
                state.paired -> PairedContent(state, onAction)
                state.role == SyncRole.Host -> HostPairing(state, onAction)
                state.role == SyncRole.Client -> ClientPairing(state, onAction)
                else -> Unit
            }
        }
    }
}

@Composable
private fun PairedContent(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
) {
    ListItem(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(segmentShape(0, 1)),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        headlineContent = { Text(state.peerName.orEmpty()) },
        supportingContent = { state.line?.let { Text(syncLineText(it)) } },
    )
    Hint(stringResource(if (state.role == SyncRole.Host) Res.string.sync_host_paired_hint else Res.string.sync_client_paired_hint))
    TextButton(onClick = { onAction(SyncAction.Unpair) }, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.sync_unpair))
    }
}

@Composable
private fun HostPairing(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
) {
    ProblemNotice(state.problem, onAction)
    val code = state.code ?: return
    Text(
        stringResource(Res.string.sync_host_instructions),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp),
    )
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = code,
        transitionSpec = { fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) },
    ) {
        Text(
            it,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 34.sp),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
    Text(
        if (state.secondsLeft > 0) {
            stringResource(Res.string.sync_code_expires, state.secondsLeft / 60, (state.secondsLeft % 60).toString().padStart(2, '0'))
        } else {
            stringResource(Res.string.sync_code_renewing)
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Hint(stringResource(Res.string.sync_same_network))
}

@Composable
private fun ClientPairing(
    state: SyncUiState,
    onAction: (SyncAction) -> Unit,
) {
    ProblemNotice(state.problem, onAction)
    Hint(stringResource(Res.string.sync_client_instructions))
    OutlinedTextField(
        value = state.codeInput,
        onValueChange = { onAction(SyncAction.EditCode(it)) },
        label = { Text(stringResource(Res.string.sync_code_label)) },
        supportingText = { Text(stringResource(Res.string.sync_code_hint)) },
        singleLine = true,
        enabled = state.step == PairingStep.Ready,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
        shape = MaterialTheme.shapes.large,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            ),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onAction(SyncAction.Pair) }),
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
    )
    Button(onClick = { onAction(SyncAction.Pair) }, enabled = state.canPair, shapes = ButtonDefaults.shapes()) {
        Text(
            stringResource(
                when (state.step) {
                    PairingStep.Ready -> Res.string.sync_pair
                    PairingStep.Searching -> Res.string.sync_searching
                    PairingStep.Connecting -> Res.string.sync_connecting
                },
            ),
        )
    }
}

@Composable
private fun ProblemNotice(
    problem: PairingFailure?,
    onAction: (SyncAction) -> Unit,
) {
    AnimatedNotice(
        notice = problem,
        text = { problemText(it) },
        actionLabel = stringResource(Res.string.sync_dismiss),
        onAction = { onAction(SyncAction.DismissProblem) },
    )
}

@Composable
private fun problemText(problem: PairingFailure): String =
    stringResource(
        when (problem) {
            PairingFailure.WrongCode -> Res.string.sync_problem_wrong_code
            PairingFailure.NotFound -> Res.string.sync_problem_not_found
            PairingFailure.Expired -> Res.string.sync_problem_expired
            PairingFailure.TooNew -> Res.string.sync_problem_too_new
            PairingFailure.NeedsPermission -> Res.string.sync_problem_needs_permission
            PairingFailure.Error -> Res.string.sync_problem_error
        },
    )

@Composable
private fun Hint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp),
    )
}

@Preview
@Composable
private fun SyncHostPreview() {
    HushTheme {
        SyncScreen(
            state = SyncUiState(role = SyncRole.Host, loading = false, code = "7K2Q-M9XD-4TRB", secondsLeft = 102),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun SyncClientPreview() {
    HushTheme {
        SyncScreen(
            state = SyncUiState(role = SyncRole.Client, loading = false, codeInput = "7k2q-m9xd", problem = PairingFailure.WrongCode),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun SyncPairedPreview() {
    HushTheme {
        SyncScreen(
            state = SyncUiState(role = SyncRole.Client, loading = false, peerName = "arch-desktop", line = SyncLine.Synced(5)),
            onAction = {},
            onBack = {},
        )
    }
}
