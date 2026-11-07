// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

sealed interface DecodeResult {
    data class Ok(
        val payload: SyncPayload,
    ) : DecodeResult

    /** Written by a newer Hush: update this one. */
    data object TooNew : DecodeResult

    data object Malformed : DecodeResult
}

/** The payload as UTF-8 JSON. Unknown keys are ignored, so a newer version can add fields. */
object SyncCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(payload: SyncPayload): ByteArray = json.encodeToString(SyncPayload.serializer(), payload).encodeToByteArray()

    fun decode(bytes: ByteArray): DecodeResult =
        try {
            // The version is read first, so a newer layout is reported as TooNew rather than Malformed.
            val element = json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: return DecodeResult.Malformed
            val version = element["v"]?.jsonPrimitive?.int ?: SYNC_PAYLOAD_VERSION
            if (version > SYNC_PAYLOAD_VERSION) {
                DecodeResult.TooNew
            } else {
                DecodeResult.Ok(json.decodeFromJsonElement(SyncPayload.serializer(), element))
            }
        } catch (_: SerializationException) {
            DecodeResult.Malformed
        } catch (_: IllegalArgumentException) {
            DecodeResult.Malformed
        }
}
