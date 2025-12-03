package com.securecall.app.ghostnet.crypto

/**
 * CRYPTO-27:
 * Einfacher Header für verschlüsselte Frames.
 *
 * Dies ist NICHT das endgültige Format, sondern ein klarer MVP:
 *
 *  - magic:   Kennung, um "unser" Format zu erkennen (z.B. 0x53,0x43 = 'S','C')
 *  - keyId:   Welche Schlüsselversion wird genutzt?
 *  - nonce:   Nonce/Zähler zu diesem Frame.
 *
 * Später:
 *  - fester Byte-Header (z.B. 1 Byte Version, 1 Byte KeyId, 8 Byte Nonce)
 *  - evtl. zusätzliche Flags (FrameType, Reserved, etc.)
 */
data class SessionCipherHeader(
    val magic: Short,
    val keyId: Int,
    val nonce: Long
) {
    companion object {
        const val MAGIC_SC: Short = 0x5343  // 'S' 'C'

        fun create(keyId: Int, nonce: Long): SessionCipherHeader {
            return SessionCipherHeader(MAGIC_SC, keyId, nonce)
        }
    }
}
