package com.securecall.app.ghostnet.frame

import android.util.Log

/**
 * CRYPTO-25:
 * Einfacher FrameRouter-Stub.
 *
 * Erwartet ein bereits entschlüsseltes ByteArray (mediaData).
 * Byte 0 wird als Typ verwendet:
 *
 *  0x01 -> Audio
 *  0x02 -> Control
 *  0x03 -> KeepAlive
 *  sonst -> Unknown
 *
 * Später:
 *  - echte Frame-Header-Struktur
 *  - Parsing in AudioFrame / ControlFrame / KeepAliveFrame
 *  - Weiterleitung an dedizierte Handler
 */
object FrameRouter {

    private const val TAG = "FRAME_ROUTER"

    fun route(mediaData: ByteArray) {
        if (mediaData.isEmpty()) {
            Log.w(TAG, "route(): empty payload")
            return
        }

        val type = mediaData[0].toInt() and 0xFF

        when (type) {
            0x01 -> handleAudio(mediaData)
            0x02 -> handleControl(mediaData)
            0x03 -> handleKeepAlive(mediaData)
            else -> {
                Log.w(TAG, "route(): unknown frame type=0x${type.toString(16)}, size=${mediaData.size}")
            }
        }
    }

    private fun handleAudio(mediaData: ByteArray) {
        Log.d(TAG, "handleAudio(): size=${mediaData.size}")
        // TODO: CRYPTO-REAL: in echten AudioFrame dekodieren & an Audio-Pipeline geben
    }

    private fun handleControl(mediaData: ByteArray) {
        Log.d(TAG, "handleControl(): size=${mediaData.size}")
        // TODO: CRYPTO-REAL: ControlFrame parsen (z.B. Mute/Unmute, Call-Signale etc.)
    }

    private fun handleKeepAlive(mediaData: ByteArray) {
        Log.d(TAG, "handleKeepAlive(): size=${mediaData.size}")
        // TODO: CRYPTO-REAL: KeepAlive-Zähler / Heartbeat-Handling
    }
}

    // CRYPTO-26: Structured Route Entry
    fun routeStructured(parsed: Any?) {
        if (parsed == null) {
            Log.w(TAG, "routeStructured(): parsed=null, skipping")
            return
        }

        when (parsed) {
            is AudioFrame -> {
                Log.d(TAG, "StructuredRoute: AudioFrame ts=${parsed.timestamp} size=${parsed.data.size}")
                handleAudioFrame(parsed)
            }

            is ControlFrame -> {
                Log.d(TAG, "StructuredRoute: ControlFrame code=${parsed.code} info=${parsed.info}")
                handleControlFrame(parsed)
            }

            is KeepAliveFrame -> {
                Log.d(TAG, "StructuredRoute: KeepAliveFrame ts=${parsed.timestamp}")
                handleKeepAliveFrame(parsed)
            }

            else -> {
                Log.w(TAG, "StructuredRoute: Unknown object=${parsed}")
            }
        }
    }

    private fun handleAudioFrame(frame: AudioFrame) {
        Log.d(TAG, "AudioFrame → Audio pipeline TBD, data=${frame.data.size}")
        // TODO: echte AudioPipeline (PCM decode → jitterbuffer → AudioTrack)
    }

    private fun handleControlFrame(frame: ControlFrame) {
        Log.d(TAG, "ControlFrame: code=${frame.code} info=${frame.info}")

        // Beispiel: Call-End-Signal melden
        if (frame.code == 200) {
            com.securecall.app.ghostnet.security.SecurityEventBus.debug(
                type = com.securecall.app.ghostnet.security.SecurityEventType.GENERIC,
                message = "ControlFrame call-end received"
            )
        }
        // TODO: echte Call-Control-Signale aber später
    }

    private fun handleKeepAliveFrame(frame: KeepAliveFrame) {
        Log.d(TAG, "KeepAlive received")
        // TODO: Heartbeat / Ping / Session Liveness Tracking
    }
