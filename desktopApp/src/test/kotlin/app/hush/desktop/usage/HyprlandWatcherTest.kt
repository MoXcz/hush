// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import app.hush.core.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class HyprlandWatcherTest {
    private val log = FocusLog(MutableClock())
    private val watcher =
        HyprlandWatcher(
            Path.of("/nonexistent"),
            log,
            CoroutineScope(Job()),
            AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined),
            MutableClock(),
        )

    @Test
    fun `a window seen after stop isn't recorded, so the closing gap stays last`() {
        watcher.start()
        watcher.record(FocusedWindow("kitty", "~"))
        watcher.stop()
        watcher.record(FocusedWindow("zen", "Video - YouTube"))
        assertEquals(listOf("kitty", null), log.entries().map { it.target?.key }.takeLast(2))
    }

    @Test
    fun `nothing is recorded before start, and a restart records again`() {
        watcher.record(FocusedWindow("kitty", "~"))
        assertEquals(emptyList(), log.entries().mapNotNull { it.target?.key })
        watcher.start()
        watcher.stop()
        watcher.start()
        watcher.record(FocusedWindow("zen", "Video - YouTube"))
        assertEquals(
            "web:youtube",
            log
                .entries()
                .last()
                .target
                ?.key,
        )
        watcher.stop()
    }
}
