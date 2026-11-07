// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.detox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DetoxPathTest {
    @Test
    fun `levels go 1 h, 1_5 h, 2 h up to the goal`() {
        assertEquals(listOf(60, 90, 120, 150, 180, 210, 240).map { it.minutes }, DetoxPath.levels(4.hours))
    }

    @Test
    fun `a 1 h goal is a single level`() {
        assertEquals(listOf(1.hours), DetoxPath.levels(1.hours))
    }

    @Test
    fun `a goal under 1 h is treated as 1 h`() {
        assertEquals(listOf(1.hours), DetoxPath.levels(20.minutes))
    }

    @Test
    fun `a goal off the half-hour grid becomes the last level`() {
        assertEquals(listOf(60, 90, 100).map { it.minutes }, DetoxPath.levels(100.minutes))
    }

    @Test
    fun `nothing passed starts at the first level`() {
        val progress = DetoxPath.progress(4.hours, longestPassed = null)
        assertEquals(0, progress.currentIndex)
        assertEquals(1.hours, progress.currentLevel)
        assertFalse(progress.complete)
    }

    @Test
    fun `passing a level unlocks the next`() {
        assertEquals(90.minutes, DetoxPath.progress(4.hours, 60.minutes).currentLevel)
        assertEquals(2.hours, DetoxPath.progress(4.hours, 90.minutes).currentLevel)
    }

    @Test
    fun `passing the goal completes the path and keeps the goal as the level`() {
        val progress = DetoxPath.progress(2.hours, 2.hours)
        assertTrue(progress.complete)
        assertEquals(2.hours, progress.currentLevel)
        assertEquals(2, progress.currentIndex)
    }

    @Test
    fun `lowering the goal below passed levels completes the path`() {
        val progress = DetoxPath.progress(90.minutes, 3.hours)
        assertTrue(progress.complete)
        assertEquals(90.minutes, progress.currentLevel)
    }

    @Test
    fun `raising the goal keeps progress and opens the next level`() {
        val progress = DetoxPath.progress(5.hours, 4.hours)
        assertFalse(progress.complete)
        assertEquals(4.hours + 30.minutes, progress.currentLevel)
    }

    @Test
    fun `progress follows the longest passed level even off the grid`() {
        assertEquals(2.hours, DetoxPath.progress(4.hours, 100.minutes).currentLevel)
    }

    @Test
    fun `only passed levels and the next one are choosable`() {
        assertEquals(listOf(1.hours), DetoxPath.progress(4.hours, longestPassed = null).choosable)
        assertEquals(listOf(60, 90, 120).map { it.minutes }, DetoxPath.progress(4.hours, 90.minutes).choosable)
    }

    @Test
    fun `after the goal is reached every level is choosable`() {
        val progress = DetoxPath.progress(2.hours, 2.hours)
        assertEquals(progress.levels, progress.choosable)
    }
}
