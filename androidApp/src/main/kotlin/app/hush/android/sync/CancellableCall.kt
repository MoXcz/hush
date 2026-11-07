// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.sync

import app.hush.sync.lan.CallHandle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

// Not `runInterruptible`: plain sockets ignore thread interrupts, so cancellation closes the socket instead.
internal suspend fun <T> cancellableCall(call: (CallHandle) -> T): T =
    coroutineScope {
        val handle = CallHandle()
        // Cancellation handlers run on the cancelling thread, so this works while [call] blocks.
        val watcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                suspendCancellableCoroutine<Unit> { it.invokeOnCancellation { handle.close() } }
            }
        try {
            call(handle)
        } finally {
            watcher.cancel()
        }
    }
