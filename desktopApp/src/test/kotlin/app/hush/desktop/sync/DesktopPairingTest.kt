// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.sync

import app.hush.sync.PairedPeer
import app.hush.sync.PairingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPairingTest {
    private fun TestScope.pairing(): DesktopPairing {
        val clock =
            object : Clock {
                override fun now() = START + testScheduler.currentTime.milliseconds
            }
        return DesktopPairing(clock, backgroundScope)
    }

    @Test
    fun `open shows a code until it runs out, then a new one`() =
        runTest {
            val pairing = pairing()
            pairing.open()
            val first = assertIs<PairingState.ShowingCode>(pairing.state.value)
            assertEquals(START + 2.minutes, first.expiresAt)
            assertEquals(14, first.code.length)
            advanceTimeBy(2.minutes - 1.milliseconds)
            runCurrent()
            assertEquals(first, pairing.state.value)
            advanceTimeBy(2.milliseconds)
            runCurrent()
            val second = assertIs<PairingState.ShowingCode>(pairing.state.value)
            assertNotEquals(first.code, second.code)
            assertEquals(START + 4.minutes, second.expiresAt)
        }

    @Test
    fun `close hides the code`() =
        runTest {
            val pairing = pairing()
            pairing.open()
            pairing.close()
            assertEquals(PairingState.Idle, pairing.state.value)
            advanceTimeBy(5.minutes)
            assertEquals(PairingState.Idle, pairing.state.value)
        }

    @Test
    fun `a finished pairing stays reported after the code closes`() =
        runTest {
            val pairing = pairing()
            pairing.open()
            val peer = PairedPeer("phone-id", "Pixel", "pin")
            pairing.paired(peer)
            pairing.close()
            assertEquals(PairingState.Paired(peer), pairing.state.value)
            pairing.open()
            assertIs<PairingState.ShowingCode>(pairing.state.value)
        }

    private companion object {
        val START = Instant.parse("2026-11-06T10:00:00Z")
    }
}
