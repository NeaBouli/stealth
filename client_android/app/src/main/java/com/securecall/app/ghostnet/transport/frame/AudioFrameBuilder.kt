package com.securecall.app.ghostnet.transport.frame

// BACKEND-39 / ANDROID-03:
// AudioFrameBuilder — universeller Erzeuger für AudioFrames
object AudioFrameBuilder {

    // Rohes PCM / RTP / unverarbeitetes Audio
    fun raw(data: ByteArray): AudioFrame {
        return AudioFrame(
            timestamp = System.currentTimeMillis(),
            type = AudioFrame.FrameType.RAW,
            data = data
        )
    }

    // Verschlüsselter Frame (SRTP / Noise / QUIC Payload)
    fun encrypted(data: ByteArray): AudioFrame {
        return AudioFrame(
            timestamp = System.currentTimeMillis(),
            type = AudioFrame.FrameType.ENCRYPTED,
            data = data
        )
    }

    // Control-Frame: z.B. DTX, Silence, KeepAlive
    fun control(flag: Byte): AudioFrame {
        return AudioFrame(
            timestamp = System.currentTimeMillis(),
            type = AudioFrame.FrameType.CONTROL,
            data = byteArrayOf(flag)
        )
    }

    // Silence Frame (Platzhalter)
    fun silence(): AudioFrame {
        return control(0x00)
    }

    // Heartbeat-Frame
    fun heartbeat(): AudioFrame {
        return control(0x7F.toByte())
    }
}
