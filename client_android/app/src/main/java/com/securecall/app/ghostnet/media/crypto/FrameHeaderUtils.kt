package com.securecall.app.ghostnet.media.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CRYPTO-07:
 * Helper zum Erzeugen und Parsen des Frame Headers.
 */
object FrameHeaderUtils {

    private const val HEADER_SIZE = 9

    fun build(header: FrameHeader, payload: ByteArray): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)

        out[0] = header.version

        val buf = ByteBuffer.allocate(8)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.putLong(header.nonce)

        val nonceBytes = buf.array()
        System.arraycopy(nonceBytes, 0, out, 1, 8)

        System.arraycopy(payload, 0, out, HEADER_SIZE, payload.size)

        return out
    }

    fun parse(raw: ByteArray): Pair<FrameHeader, ByteArray> {
        if (raw.size < HEADER_SIZE) {
            throw IllegalArgumentException("Frame zu klein (<9 bytes)")
        }

        val version = raw[0]

        val nonceBuf = ByteBuffer.wrap(raw, 1, 8)
        nonceBuf.order(ByteOrder.BIG_ENDIAN)
        val nonce = nonceBuf.long

        val payload = raw.copyOfRange(HEADER_SIZE, raw.size)

        return FrameHeader(version, nonce) to payload
    }

    fun headerSize(): Int = HEADER_SIZE
}
