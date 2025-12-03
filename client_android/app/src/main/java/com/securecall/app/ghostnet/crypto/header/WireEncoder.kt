package com.securecall.app.ghostnet.crypto.header

/**
 * CRYPTO-19:
 * Minimaler Encoder für GhostNet WireHeader + Payload.
 *
 * Format (MVP):
 *  [0]      = version (1 Byte)
 *  [1..8]   = nonce (8 Bytes, BigEndian)
 *  [9..end] = payload (ciphertext / raw bytes)
 */

object WireEncoder {

    fun encode(header: WireHeader, payload: ByteArray): ByteArray {
        val out = ByteArray(1 + 8 + payload.size)

        // Version
        out[0] = header.version.toByte()

        // Nonce (BigEndian)
        var nonce = header.nonce
        for (i in 8 downTo 1) {
            out[i] = (nonce and 0xFF).toByte()
            nonce = nonce shr 8
        }

        // Payload
        System.arraycopy(payload, 0, out, 9, payload.size)

        return out
    }

    /**
     * Helper für Debug / MVP:
     * Erzeugt einen Header mit gegebener Nonce und Version=1.
     */
    fun buildHeaderWithNonce(nonce: Long, version: Int = 1): WireHeader {
        return WireHeader(
            version = version,
            nonce = nonce
        )
    }
}
