// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync.lan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

internal class FrameTooLargeException(
    length: Int,
    limit: Int,
) : IOException("frame of $length bytes, limit $limit")

// A frame is a 4-byte big-endian length followed by that many bytes.
internal object Frames {
    const val CONTROL_LIMIT = 4 * 1024
    const val PAYLOAD_LIMIT = 64 * 1024 * 1024

    fun write(
        out: DataOutputStream,
        bytes: ByteArray,
        limit: Int,
    ) {
        if (bytes.size > limit) throw FrameTooLargeException(bytes.size, limit)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun read(
        input: DataInputStream,
        limit: Int,
    ): ByteArray {
        val length = input.readInt()
        if (length < 0 || length > limit) throw FrameTooLargeException(length, limit)
        return ByteArray(length).also { input.readFully(it) }
    }
}
