package com.securecall.app.ghostnet.media.decoder

import android.util.Log

/**
 * PATCH 239:
 * Dummy-Decoder nimmt Bytes und erzeugt ein ShortArray.
 * Kein AudioCodec – rein synthetisch.
 */
object DummyDecoder {

    private const val TAG = "DUMMY_DECODER"

    fun decode(raw: ByteArray): ShortArray {
        Log.d(TAG, "decode(): raw size=${raw.size}")
        val pcm = ShortArray(raw.size)

        for (i in raw.indices) {
            pcm[i] = (raw[i].toInt() * 128).toShort()
        }

        postDecodeEvent(raw.size, pcm.size)
        return pcm
    }

    // PATCH 240: Debug-Event nach Decode
    private fun postDecodeEvent(rawSize: Int, pcmSize: Int) {
        com.securecall.app.debug.GhostDebugEventBus.post(
            "DECODER",
            "decoded rawSize=$rawSize -> pcmSize=$pcmSize"
        )
    }
}
