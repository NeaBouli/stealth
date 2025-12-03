package com.securecall.app.ghostnet.media.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.securecall.app.ghostnet.media.crypto.CiphertextFrame

/**
 * CRYPTO-11:
 * Offizielles Wire-Format für verschlüsselte Frames:
 *
 * [1 byte ] version
 * [8 bytes] nonce        (UInt64 big endian)
 * [4 bytes] length       (UInt32 big endian)
 * [n bytes] ciphertext
 */

object CiphertextWireFormat {

    fun toByteArray(frame: CiphertextFrame): ByteArray {
        val header = frame.header
        val payload = frame.ciphertext

        val buf = ByteBuffer.allocate(1 + 8 + 4 + payload.size)
        buf.order(ByteOrder.BIG_ENDIAN)

        buf.put(header.version)
        buf.putLong(header.nonce)
        buf.putInt(payload.size)
        buf.put(payload)

        return buf.array()
    }

    fun fromByteArray(raw: ByteArray): CiphertextFrame {
        val buf = ByteBuffer.wrap(raw)
        buf.order(ByteOrder.BIG_ENDIAN)

        val version = buf.get()
        val nonce = buf.getLong()
        val length = buf.getInt()

        if (length < 0 || length > (raw.size - 13)) {
            throw IllegalArgumentException("Ciphertext length invalid: $length")
        }

        val payload = ByteArray(length)
        buf.get(payload)

        val header = FrameHeader(version, nonce)

        return CiphertextFrame(
            header = header,
            ciphertext = payload
        )
    }
}
