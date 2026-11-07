// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocklists.ui

import app.hush.blocker.BlockableApp
import app.hush.blocker.fake.FakeBlocker
import app.hush.blocklists.FakeBlockListRepository
import app.hush.blocklists.ProfileRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class BlockListViewModelTest {
    private val deep = ProfileRole.DeepWork.profileId
    private val blocker =
        FakeBlocker().apply {
            apps = listOf(BlockableApp("browser", "Browser"), BlockableApp("mail", "Mail"), BlockableApp("video", "Video"))
        }
    private val blockLists = FakeBlockListRepository(deepWork = setOf("video"), detox = emptySet())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): BlockListViewModel =
        BlockListViewModel(deep.value, blockLists, blocker).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    @Test
    fun `lists installed apps with the ones already on the list first`() =
        runTest {
            val state = viewModel().uiState.value
            assertFalse(state.loading)
            assertEquals("Deep work", state.name)
            assertEquals(listOf("video", "browser", "mail"), state.apps.map { it.packageName })
            assertEquals(setOf("video"), state.selected)
        }

    @Test
    fun `ticking saves at once, and ticking again removes the app`() =
        runTest {
            val vm = viewModel()
            vm.onAction(BlockListAction.Toggle("mail"))
            assertEquals(setOf("video", "mail"), blockLists.lists.first()[0].packages)
            vm.onAction(BlockListAction.Toggle("video"))
            assertEquals(setOf("mail"), vm.uiState.value.selected)
            // The order stays as it was when the screen opened.
            assertEquals(
                listOf("video", "browser", "mail"),
                vm.uiState.value.apps
                    .map { it.packageName },
            )
        }

    @Test
    fun `search filters by label, ignoring case`() =
        runTest {
            val vm = viewModel()
            vm.onAction(BlockListAction.Search("MA"))
            assertEquals(
                listOf("mail"),
                vm.uiState.value.apps
                    .map { it.packageName },
            )
            vm.onAction(BlockListAction.Search(""))
            assertEquals(3, vm.uiState.value.apps.size)
        }

    @Test
    fun `editing one list leaves the other alone`() =
        runTest {
            viewModel().onAction(BlockListAction.Toggle("browser"))
            assertEquals(emptySet(), blockLists.lists.first()[1].packages)
        }
}
