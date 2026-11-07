// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingCodesTest {
    private var now = 1_000_000L
    private val changes = mutableListOf<ShownCode?>()
    private val codes = PairingCodes(clock = { now }, onChange = { changes += it })

    @Test
    fun `codes are 12 Crockford base32 characters and don't repeat`() {
        val random = SecureRandom()
        val generated = List(1_000) { PairingCodes.generate(random) }
        assertTrue(generated.all { code -> code.length == 12 && code.all { it in PairingCodes.ALPHABET } })
        assertEquals(1_000, generated.toSet().size)
        assertEquals(32, PairingCodes.ALPHABET.toSet().size)
    }

    @Test
    fun `typed codes are normalised`() {
        assertEquals("ABCDEFGHJKMN", PairingCodes.normalize("abcd-efgh-jkmn"))
        assertEquals("0011ABCDEFGH", PairingCodes.normalize(" oO iL abcd efgh "))
        assertNull(PairingCodes.normalize("ABCD-EFGH-JKM"))
        assertNull(PairingCodes.normalize("ABCD-EFGH-JKMNP"))
        assertNull(PairingCodes.normalize("ABCD-EFGH-JKMU"))
        assertNull(PairingCodes.normalize(""))
    }

    @Test
    fun `display groups the code in fours`() {
        assertEquals("ABCD-EFGH-JKMN", ShownCode("ABCDEFGHJKMN", 0).display)
    }

    @Test
    fun `an open code lasts two minutes, then rotates`() {
        val shown = codes.open()
        assertEquals(now + 120_000, shown.expiresAtMillis)
        now += 119_999
        assertEquals(shown, codes.current())
        now += 1
        val next = codes.current()!!
        assertNotEquals(shown.code, next.code)
        assertEquals(listOf<ShownCode?>(shown, next), changes)
    }

    @Test
    fun `checking the state doesn't use up the code`() {
        val shown = codes.open()
        repeat(5) { assertEquals(PairingCodes.State.Open, codes.state()) }
        assertEquals(shown, codes.current())
    }

    @Test
    fun `a right proof is accepted once and uses up the code`() {
        val shown = codes.open()
        assertEquals(PairingCodes.Attempt.Accepted(shown.code), codes.attempt { it == shown.code })
        assertNotEquals(shown.code, codes.current()!!.code)
        assertEquals(PairingCodes.Attempt.WrongCode, codes.attempt { it == shown.code })
    }

    @Test
    fun `a wrong proof uses up the code`() {
        val shown = codes.open()
        assertEquals(PairingCodes.Attempt.WrongCode, codes.attempt { false })
        assertNotEquals(shown.code, codes.current()!!.code)
    }

    @Test
    fun `an expired code fails and rotates`() {
        val shown = codes.open()
        now += 120_000
        assertEquals(PairingCodes.State.Expired, codes.state())
        val next = codes.current()!!
        assertNotEquals(shown.code, next.code)
        assertEquals(now + 120_000, next.expiresAtMillis)
        now += 120_000
        assertEquals(PairingCodes.Attempt.Expired, codes.attempt { true })
        assertNotEquals(next.code, codes.current()!!.code)
    }

    @Test
    fun `of many proofs arriving together only one is accepted`() {
        val shown = codes.open()
        val threads = 16
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results =
                List(threads) {
                    pool.submit(
                        Callable {
                            start.await()
                            codes.attempt { it == shown.code }
                        },
                    )
                }
            start.countDown()
            val accepted = results.map { it.get(5, TimeUnit.SECONDS) }.filterIsInstance<PairingCodes.Attempt.Accepted>()
            assertEquals(1, accepted.size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `nothing can be attempted while closed`() {
        assertEquals(PairingCodes.State.Closed, codes.state())
        assertEquals(PairingCodes.Attempt.Closed, codes.attempt { true })
        codes.open()
        codes.close()
        assertNull(codes.current())
        assertEquals(PairingCodes.Attempt.Closed, codes.attempt { true })
        assertNull(changes.last())
    }
}
