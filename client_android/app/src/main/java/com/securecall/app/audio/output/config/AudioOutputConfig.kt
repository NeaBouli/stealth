package com.securecall.app.audio.output.config

// BACKEND-47 / ANDROID-03:
// Konfiguration für AudioTrack (Platzhalter)
// Später über Settings oder negotiated Codec festgelegt.
object AudioOutputConfig {

    const val SAMPLE_RATE = 16000       // 16 kHz (typisch VoIP)
    const val CHANNELS = 1              // Mono
    const val ENCODING = 2              // AudioFormat.ENCODING_PCM_16BIT placeholder

    // Platzhalter-Puffergröße
    const val BUFFER_SIZE = 2048
}
