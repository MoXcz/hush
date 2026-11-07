// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class FramesTest {
    private fun framed(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        Frames.write(DataOutputStream(out), bytes, Frames.PAYLOAD_LIMIT)
        return out.toByteArray()
    }

    private fun lengthOnly(length: Int): DataInputStream {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).writeInt(length)
        return DataInputStream(out.toByteArray().inputStream())
    }

    @Test
    fun `frame round trips with a big-endian length`() {
        val bytes = framed(byteArrayOf(7, 8, 9))
        assertContentEquals(byteArrayOf(0, 0, 0, 3, 7, 8, 9), bytes)
        assertContentEquals(byteArrayOf(7, 8, 9), Frames.read(DataInputStream(bytes.inputStream()), Frames.CONTROL_LIMIT))
    }

    @Test
    fun `control frame of exactly the limit is accepted`() {
        val bytes = framed(ByteArray(Frames.CONTROL_LIMIT))
        Frames.read(DataInputStream(bytes.inputStream()), Frames.CONTROL_LIMIT)
    }

    @Test
    fun `length over the limit is refused before reading or allocating`() {
        assertFailsWith<FrameTooLargeException> { Frames.read(lengthOnly(Frames.CONTROL_LIMIT + 1), Frames.CONTROL_LIMIT) }
        assertFailsWith<FrameTooLargeException> { Frames.read(lengthOnly(Frames.PAYLOAD_LIMIT + 1), Frames.PAYLOAD_LIMIT) }
        assertFailsWith<FrameTooLargeException> { Frames.read(lengthOnly(Int.MAX_VALUE), Frames.PAYLOAD_LIMIT) }
    }

    @Test
    fun `negative length is refused`() {
        assertFailsWith<FrameTooLargeException> { Frames.read(lengthOnly(-1), Frames.CONTROL_LIMIT) }
    }

    @Test
    fun `writing over the limit is refused`() {
        assertFailsWith<FrameTooLargeException> {
            Frames.write(DataOutputStream(ByteArrayOutputStream()), ByteArray(Frames.CONTROL_LIMIT + 1), Frames.CONTROL_LIMIT)
        }
    }

    @Test
    fun `truncated frame fails`() {
        val bytes = framed(ByteArray(10)).copyOf(8)
        assertFailsWith<EOFException> { Frames.read(DataInputStream(bytes.inputStream()), Frames.CONTROL_LIMIT) }
    }
}
