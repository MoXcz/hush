// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MutableClock(
    var now: Instant = Instant.fromEpochMilliseconds(1_000_000_000L),
) : Clock {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}

class FocusLogTest {
    private val kitty = FocusTarget("kitty", null)
    private val zen = FocusTarget("zen", null)
    private val youtube = FocusTarget("web:youtube", "YouTube")

    private fun entry(
        at: Long,
        target: FocusTarget?,
    ) = FocusEntry(at * 1000, target)

    private fun perTarget(
        entries: List<FocusEntry>,
        start: Long,
        end: Long,
        now: Long = end,
        excluded: Set<String> = emptySet(),
    ) = FocusTime.perTarget(entries, start * 1000, end * 1000, now * 1000, excluded).mapValues { it.value / 1000 }

    @Test
    fun `a browser window on a site is counted for the site, not the browser`() {
        assertEquals(youtube, FocusTarget.of(FocusedWindow("zen", "Video - YouTube — Zen Browser")))
        assertEquals(zen, FocusTarget.of(FocusedWindow("zen", "New Tab — Zen Browser")))
        assertEquals(kitty, FocusTarget.of(FocusedWindow("kitty", "Video - YouTube")))
    }

    @Test
    fun `each window counts until the next change, clipped to the phase`() {
        val entries = listOf(entry(0, kitty), entry(100, youtube), entry(160, zen), entry(200, kitty))
        assertEquals(mapOf(kitty to 70L, youtube to 60L, zen to 40L), perTarget(entries, 50, 220))
    }

    @Test
    fun `the last window counts until now when the phase hasn't ended`() {
        assertEquals(mapOf(kitty to 30L), perTarget(listOf(entry(0, kitty)), 10, 100, now = 40))
    }

    @Test
    fun `gaps count for nothing`() {
        val entries = listOf(entry(0, kitty), entry(20, null), entry(80, kitty))
        assertEquals(mapOf(kitty to 40L), perTarget(entries, 0, 100))
    }

    @Test
    fun `time before the first entry is unknown`() {
        assertEquals(mapOf(kitty to 50L), perTarget(listOf(entry(50, kitty)), 0, 100))
    }

    @Test
    fun `the same site seen in several visits adds up`() {
        val entries = listOf(entry(0, youtube), entry(10, kitty), entry(20, youtube), entry(30, null))
        assertEquals(mapOf(youtube to 20L, kitty to 10L), perTarget(entries, 0, 100))
    }

    @Test
    fun `short visits and excluded windows are left out`() {
        val hush = FocusTarget(HUSH_WINDOW_CLASS, null)
        val entries = listOf(entry(0, hush), entry(60, zen), entry(64, kitty))
        assertEquals(mapOf(kitty to 36L), perTarget(entries, 0, 100, excluded = setOf(HUSH_WINDOW_CLASS)))
    }

    @Test
    fun `entries after the phase are ignored`() {
        assertEquals(emptyMap(), perTarget(listOf(entry(200, kitty)), 0, 100, now = 300))
    }

    @Test
    fun `the log skips repeats and keeps the window that had focus at the cutoff`() {
        val clock = MutableClock()
        val log = FocusLog(clock, keep = 1.hours)
        log.record(FocusedWindow("kitty", "a"))
        clock.advance(10.seconds)
        log.record(FocusedWindow("kitty", "b"))
        assertEquals(1, log.entries().size)
        log.record(FocusedWindow("zen", "x"))
        clock.advance(2.hours)
        log.record(null)
        clock.advance(1.seconds)
        log.record(FocusedWindow("kitty", "c"))
        assertEquals(listOf(zen, null, kitty), log.entries().map { it.target })
    }
}
