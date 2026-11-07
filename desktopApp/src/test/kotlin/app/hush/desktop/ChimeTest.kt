// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChimeTest {
    @Test
    fun `the chime is under a second of quiet 16-bit mono sound`() {
        val bytes = Chime.samples()
        val seconds = bytes.size / 2.0 / Chime.SAMPLE_RATE
        assertEquals(0.85, seconds, 0.01)
        val peak =
            (bytes.indices step 2).maxOf { i ->
                abs(((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt())
            }
        assertTrue(peak in 1..(Chime.PEAK * Short.MAX_VALUE).toInt() + 1, "peak $peak")
    }
}
