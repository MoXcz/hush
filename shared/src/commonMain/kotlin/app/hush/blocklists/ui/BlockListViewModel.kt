// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hush.blocker.BlockableApp
import app.hush.blocker.BlockerSetup
import app.hush.blocker.ProfileId
import app.hush.blocklists.BlockListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class BlockListUiState(
    val name: String = "",
    val loading: Boolean = true,
    /** Installed apps matching [query]; the ones on the list when the screen opened come first. */
    val apps: List<BlockableApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
)

sealed interface BlockListAction {
    data class Search(
        val query: String,
    ) : BlockListAction

    data class Toggle(
        val packageName: String,
    ) : BlockListAction
}

/** Picks the apps of one block list. Every tap is saved right away. */
class BlockListViewModel(
    listId: String,
    private val repository: BlockListRepository,
    setup: BlockerSetup,
) : ViewModel() {
    private val id = ProfileId(listId)
    private val installed = MutableStateFlow<List<BlockableApp>?>(null)
    private val query = MutableStateFlow("")
    private val list = repository.lists.map { all -> all.firstOrNull { it.id == id } }
    private val writes = Mutex()

    val uiState: StateFlow<BlockListUiState> =
        combine(installed, list, query) { installed, list, query ->
            BlockListUiState(
                name = list?.name.orEmpty(),
                loading = installed == null,
                apps = installed.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) },
                selected = list?.packages.orEmpty(),
                query = query,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockListUiState())

    init {
        viewModelScope.launch {
            val apps = setup.blockableApps()
            // Ordered once, so a row doesn't jump away when it's ticked.
            val onList = list.first()?.packages.orEmpty()
            installed.value = apps.sortedBy { it.packageName !in onList }
        }
    }

    fun onAction(action: BlockListAction) {
        when (action) {
            is BlockListAction.Search -> {
                query.value = action.query
            }

            is BlockListAction.Toggle -> {
                viewModelScope.launch {
                    // Read and write under one lock, so quick taps can't undo each other.
                    writes.withLock {
                        val current = list.first()?.packages.orEmpty()
                        val next = if (action.packageName in current) current - action.packageName else current + action.packageName
                        repository.setApps(id, next)
                    }
                }
            }
        }
    }
}
