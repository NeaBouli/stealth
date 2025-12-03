package com.securecall.app.ghostnet.media

import android.util.Log

object GhostMediaRouter {

    private const val TAG = "MEDIA_ROUTER"

    // BACKEND-63: Entry-Point für MediaFrames
    fun route(frame: MediaFrame) {
        Log.d(TAG, "MediaFrame routed: size=${frame.data.size}, ts=${frame.timestamp}")

        // später:
        // - decrypt
        // - jitter buffer
        // - decode (Opus)
        // - play
    }
}

    // BACKEND-64: Decrypt-Schicht vor weiterer Verarbeitung
    private fun decryptFrame(frame: MediaFrame): MediaFrame {
        return com.securecall.app.ghostnet.media.crypto.MediaDecrypt.decrypt(frame)
    }

    // BACKEND-64: Decrypt-Pipeline einbauen
    private fun decrypt(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decrypt(frame)
    }

    // innerhalb von route():
    val raw = decrypt(frame)

    // später: decode(raw) → play()

    // BACKEND-65: Decode-Etappe
    private fun decodeAudio(bytes: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decode.MediaDecoder.decode(bytes)
    }

    // BACKEND-65: Audio decoding
    val pcm = decodeAudio(raw)

    // später: playAudio(pcm)

    // BACKEND-65: Debug Logging
    android.util.Log.d(TAG, "Decoded PCM samples: len=" + pcm.size)

    // BACKEND-66: Playback-Schicht – ruft AudioTrack-Wrapper auf
    private fun playPcm(pcm: ShortArray) {
        try {
            com.securecall.app.ghostnet.media.playback.AudioPlayer.play(pcm)
            android.util.Log.d(TAG, "playPcm(): sent ${pcm.size} samples to AudioPlayer")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "playPcm() failed", t)
        }
    }

    // BACKEND-66: nach decodeAudio(raw) aufrufen
    // Beispiel (innerhalb von route()):
    // val raw = decrypt(frame)
    // val pcm = decodeAudio(raw)
    // playPcm(pcm)

    // PATCH 211 — Haupt-Routingkette: decrypt → decode → playback
    private fun handle(frame: MediaFrame) {
        // 1) Decrypt
        val raw = decrypt(frame)

        // 2) Decode (Stub → PCM ShortArray)
        val pcm = decode(raw)

        // 3) Playback
        com.securecall.app.ghostnet.media.audio.AudioPlayback.play(pcm)
    }

    // PATCH 211 — Integration in bestehendes route()
    fun route(frame: MediaFrame) {
        Log.d("MEDIA_ROUTER", "route(): MediaFrame size=${frame.data.size}")
        handle(frame)
    }

    // PATCH 211 — ByteArray → ShortArray Konverter
    private fun toShorts(raw: ByteArray): ShortArray {
        val out = ShortArray(raw.size / 2)
        var i = 0
        var j = 0
        while (i < raw.size - 1) {
            val low = raw[i].toInt() and 0xFF
            val high = raw[i + 1].toInt() shl 8
            out[j] = (high or low).toShort()
            i += 2
            j += 1
        }
        return out
    }

    // PATCH 211 — decode() Wrapper
    private fun decode(raw: ByteArray): ShortArray {
        return toShorts(raw)   // placeholder: fake PCM
    }

    // PATCH 214 — Medien-Decodierung via AudioDecoder
    private fun decodeViaCodec(raw: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decoder.AudioDecoder.decode(raw)
    }

    // PATCH 214 — decode() mit Codec-Layer austauschen
    private fun handleWithCodec(frame: MediaFrame) {
        val raw = decrypt(frame)
        val pcm = decodeViaCodec(raw)
        com.securecall.app.ghostnet.media.audio.AudioPlayback.play(pcm)
    }

    // PATCH 224: new decoder context
    private val decoderCtx = com.securecall.app.ghostnet.media.DecoderContext()

    private fun ensureDecoder() {
        // später: sampleRate/channels aus Session-State lesen
        decoderCtx.prepareNativeDecoder(48000, 1)
    }

    // PATCH 224: inside route()
    ensureDecoder()

    // PATCH 224: reset decoder (stub)
    fun resetDecoderStub() {
        decoderCtx.freeNativeDecoder()
        android.util.Log.d("MEDIA_ROUTER", "Decoder Stub Reset")
        ensureDecoder()
    }

    // PATCH 225: main decode entry
    private fun decode(raw: ByteArray): ShortArray {
        android.util.Log.d("MEDIA_ROUTER", "decode(): raw size=${raw.size}")

        // später: native decode via decoderCtx.getNativeHandle()
        // jetzt: stub PCM (stille)
        val pcm = ShortArray(480) { 0 }
        return pcm
    }

    // PATCH 225: inside route(), after decrypt()
    val pcm = decode(raw)

    // später: play(pcm)
    android.util.Log.d("MEDIA_ROUTER", "PCM decoded size=${pcm.size}")

    // PATCH 225: reset full media pipeline (stub)
    fun resetPipelineStub() {
        android.util.Log.d("MEDIA_ROUTER", "resetPipelineStub(): full reset")
        decoderCtx.freeNativeDecoder()
        ensureDecoder()
    }

    // PATCH 226: AudioPlayer einbinden
    private val audioPlayer =
        com.securecall.app.ghostnet.media.playback.GhostAudioPlayer(48000, 1)

    private fun playPcm(pcm: ShortArray) {
        audioPlayer.write(pcm)
    }

    // PATCH 226: inside route()
    playPcm(pcm)

    // PATCH 226: debug beep passthrough
    fun testBeep(pcm: ShortArray) {
        playPcm(pcm)
    }

    // PATCH 227: use DecoderContext for decode logic
    private fun decode(raw: ByteArray): ShortArray {
        android.util.Log.d("MEDIA_ROUTER", "decode(): delegating to DecoderContext, size=${raw.size}")
        return decoderCtx.decodeStub(raw)
    }

    // PATCH 227: decode → playback wiring inside route()
    private fun handleDecodedPcm(pcm: ShortArray) {
        playPcm(pcm)
    }

    // PATCH 227: inside route() after pcm is decoded
    handleDecodedPcm(pcm)

    // PATCH 227: debug flow
    private fun logFlowEvent(stage: String) {
        android.util.Log.d("MEDIA_FLOW", "stage=$stage")
    }

    logFlowEvent("decrypt → decode → playback")

    // PATCH 230: error hook
    private fun handleDecodeError(t: Throwable) {
        android.util.Log.e("GHOST_CALL", "MediaRouter decode error", t)
        com.securecall.app.ghostnet.call.GhostCallController.terminateCall()
    }

    // PATCH 230: wrap decode with soft-kill
    private fun safeDecode(raw: ByteArray): ShortArray? {
        return try {
            decode(raw)
        } catch (t: Throwable) {
            handleDecodeError(t)
            null
        }
    }

    // PATCH 230: replace direct decode() call inside route()
    val pcm = safeDecode(raw) ?: return

    // PATCH 231 — graceful shutdown of media layer
    fun quietShutdown() {
        android.util.Log.d("MEDIA_ROUTER", "quietShutdown(): stopping media")

        audioPlayer.stop()
        audioPlayer.release()

        decoderCtx.freeNativeDecoder()
    }

// PATCH 237: media reset stub
fun resetMedia() {
    android.util.Log.w("MEDIA_ROUTER", "resetMedia(): future decoder cleanup here")
}

    // PATCH 239: dummy decode + sink
    private fun decode(raw: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decoder.DummyDecoder.decode(raw)
    }

    private fun play(pcm: ShortArray) {
        com.securecall.app.ghostnet.media.DummyPlaybackSink.play(pcm)
    }

    // PATCH 239: replace end of route() pipeline
    val pcm = safeDecode(raw) ?: return
    play(pcm)

    // PATCH 241: ensure router has a global singleton access
    private object Holder { val INSTANCE = GhostMediaRouter() }

    companion object {
        fun get(): GhostMediaRouter = Holder.INSTANCE
    }

    // PATCH 251: CryptoContext-Referenz (optional)
    private var cryptoContext: com.securecall.app.ghostnet.crypto.SessionCryptoContext? = null

    fun attachCryptoContext(ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext) {
        android.util.Log.d("MEDIA_ROUTER", "attachCryptoContext(): " + ctx.debugSummary())
        cryptoContext = ctx
    }

    fun getCryptoContext(): com.securecall.app.ghostnet.crypto.SessionCryptoContext? = cryptoContext

    // CRYPTO-06: use key-aware decrypt (still placeholder)
    private fun decryptWithKey(frame: MediaFrame): MediaFrame {
        val ctx = cryptoContext
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptWithKey(frame, ctx)
    }

    // CRYPTO-06: debug entry for decryptWithKey()
    fun debugDecryptWithKey(frame: MediaFrame) {
        decryptWithKey(frame)
    }

    // CRYPTO-07: parse inbound header
    fun parseInboundFrame(raw: ByteArray): com.securecall.app.ghostnet.media.EncryptedFrame {
        val (header, payload) =
            com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.parse(raw)

        return com.securecall.app.ghostnet.media.EncryptedFrame(
            version = header.version,
            nonce = header.nonce,
            ciphertext = payload
        )
    }

    // CRYPTO-07: Debug
    fun debugParseInbound(raw: ByteArray) {
        val f = parseInboundFrame(raw)
        android.util.Log.d(
            "MEDIA_ROUTER",
            "Parsed header: version=${f.version}, nonce=${f.nonce}, ciphertextLen=${f.ciphertext.size}"
        )
    }

    // CRYPTO-10: Debug – CiphertextFrame inspizieren
    fun debugInspectCiphertextFrame(frame: com.securecall.app.ghostnet.media.crypto.CiphertextFrame) {
        val h = frame.header
        val size = frame.ciphertext.size
        android.util.Log.d("MEDIA_ROUTER", "CiphertextFrame: version=${h.version}, nonce=${h.nonce}, size=$size")
    }

    // CRYPTO-11: Debug Wire-Format Parser
    fun debugParseWireFrame(raw: ByteArray) {
        try {
            val cf = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            android.util.Log.d(
                "WIRE_PARSE",
                "Parsed WireFrame: version=${cf.header.version}, nonce=${cf.header.nonce}, cipherLen=${cf.ciphertext.size}"
            )
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_PARSE", "Error parsing WireFrame", t)
        }
    }

    // CRYPTO-12: Debug – vollständige Validierung eines Wire-Frames
    fun debugValidateWireFrame(raw: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            val ok = com.securecall.app.ghostnet.media.crypto.CiphertextValidator.isValid(frame)
            android.util.Log.d(
                "WIRE_VALIDATE",
                "WireFrame validation result = $ok (version=${frame.header.version}, nonce=${frame.header.nonce}, len=${frame.ciphertext.size})"
            )
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_VALIDATE", "Exception while validating WireFrame", t)
        }
    }

    // CRYPTO-13: Replay Detection Hook
    private fun detectReplay(nonce: Long) {
        com.securecall.app.ghostnet.crypto.ReplayDetector.check(nonce)
    }

    // CRYPTO-13: integrate replay detection
    fun debugParseWireFrame_withReplay(raw: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            detectReplay(frame.header.nonce)

            android.util.Log.d(
                "WIRE_PARSE",
                "Parsed WF (with replay-check): version=${frame.header.version}, nonce=${frame.header.nonce}, len=${frame.ciphertext.size}"
            )
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_PARSE", "Replay-Parse Error", t)
        }
    }

    // CRYPTO-14: replay detection with security layer
    fun debugParseWireFrame_withSecurity(raw: ByteArray) {
        try {
            val f = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            com.securecall.app.ghostnet.crypto.ReplayDetector.checkWithSecurity(f.header.nonce)

            android.util.Log.d("WIRE_SEC", "Parsed with Security: version=${f.header.version}, nonce=${f.header.nonce}")
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_SEC", "Security parse error", t)
        }
    }

    // CRYPTO-17: Debug – parse header with WireHeaderParser
    fun debugParseWireHeader(raw: ByteArray) {
        try {
            val header = com.securecall.app.ghostnet.crypto.header.WireHeaderParser.parse(raw)
            if (header != null) {
                android.util.Log.d(
                    "WIRE_HDR",
                    "Parsed Header: version=${header.version}, nonce=${header.nonce}"
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_HDR", "Header parse error", t)
        }
    }

    // CRYPTO-18:
    // Einheitlicher Inbound-Pipeline-Einstiegspunkt
    fun processInboundRaw(raw: ByteArray) {
        try {
            // 1) Header parsen
            val header = com.securecall.app.ghostnet.crypto.header.WireHeaderParser.parse(raw)
            if (header == null) {
                android.util.Log.w("MEDIA_PIPE", "Header invalid, continuing anyway (debug phase)")
                return  // debug-phase: nichts tun
            }

            // 2) ReplayDetector prüfen
            com.securecall.app.ghostnet.security.ReplayDetector.checkAndReport(header.nonce)

            // 3) Rohdaten an decrypt() weiterreichen (wenn vorhanden)
            //    Decryptor arbeitet mit MediaFrame, also dummy Frame bauen
            val dummyFrame = com.securecall.app.ghostnet.media.MediaFrame(
                data = raw,
                timestamp = System.currentTimeMillis()
            )

            val decrypted = decrypt(dummyFrame)

            // 4) Weiter in safeDecode()
            val pcm = safeDecode(decrypted) ?: return

            // 5) Abspielen / weitere Verarbeitung
            playPcm(pcm)

        } catch (t: Throwable) {
            android.util.Log.e("MEDIA_PIPE", "Exception in processInboundRaw()", t)
            com.securecall.app.ghostnet.security.SecurityEventBus.post(
                com.securecall.app.ghostnet.security.SecurityEvent(
                    type = com.securecall.app.ghostnet.security.SecurityEventType.DECRYPT_FAIL,
                    message = "Exception in inbound pipeline: ${t.message}"
                )
            )
        }
    }

    // CRYPTO-18: Dummy Audio-Playback (wird später ersetzt)
    private fun playPcm(pcm: ShortArray) {
        android.util.Log.d("MEDIA_PLAY", "playPcm(): received ${pcm.size} samples (dummy)")
    }

    // CRYPTO-19: Debug — End-to-End Roundtrip (encode -> parse -> pipeline)
    fun debugEncodeRoundtrip(payloadSize: Int = 32) {
        try {
            // 1) Fake Payload
            val payload = ByteArray(payloadSize)
            kotlin.random.Random.Default.nextBytes(payload)

            // 2) Header bauen (Nonce = aktuelle Zeit)
            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .buildHeaderWithNonce(nonce)

            // 3) Wire-Frame encoden
            val encoded = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .encode(header, payload)

            android.util.Log.d(
                "WIRE_RT",
                "Encoded frame: size=${encoded.size}, nonce=$nonce"
            )

            // 4) Header separat debug-parsen
            debugParseWireHeader(encoded)

            // 5) Kompletten Frame durch die Inbound-Pipeline schicken
            processInboundRaw(encoded)

        } catch (t: Throwable) {
            android.util.Log.e("WIRE_RT", "Error in debugEncodeRoundtrip()", t)
        }
    }

    // CRYPTO-22: Parse decrypted payload into FrameTypes
    private fun parseFrameTypes(raw: ByteArray): Any? {
        return try {
            com.securecall.app.ghostnet.frame.FrameParser.parse(raw)
        } catch (t: Throwable) {
            android.util.Log.e("FRAME_PARSE", "FrameType parse error", t)
            null
        }
    }

    // PATCH CRYPTO-22: inside processInboundRaw(), right after decrypt()
    val parsedFrame = parseFrameTypes(decrypted)
    if (parsedFrame != null) {
        android.util.Log.d("FRAME_IN", "Received FrameType: " + parsedFrame)
    }

    // CRYPTO-24: WireCryptoStub decrypt integration (before MediaDecrypt)
    private fun decryptWirePayload(cipher: ByteArray): ByteArray {
        return try {
            com.securecall.app.ghostnet.crypto.WireCryptoStub.decryptPayload(cipher)
        } catch (t: Throwable) {
            android.util.Log.e("WIRE_CRYPTO", "decryptWirePayload() error", t)
            // Debug-Phase: wir geben einfach das Original zurück
            cipher
        }
    }

    // CRYPTO-24: inside processInboundRaw() after header parsing
    val cipherData = decryptWirePayload(raw)

    // CRYPTO-24: MediaDecrypt receives the cipherData from WireCryptoStub
    private fun decryptMediaFrame(cipherData: ByteArray): ByteArray {
        val dummyFrame = com.securecall.app.ghostnet.media.MediaFrame(
            data = cipherData,
            timestamp = System.currentTimeMillis()
        )
        return decrypt(dummyFrame)
    }

    // CRYPTO-24: decrypt media layer
    val mediaData = decryptMediaFrame(cipherData)

    // CRYPTO-24: FrameType parsing uses decrypted mediaData
    val parsedFrame2 = parseFrameTypes(mediaData)
    if (parsedFrame2 != null) {
        android.util.Log.d("FRAME_PIPE", "Frame after WireCrypto + MediaDecrypt: " + parsedFrame2)
    }

    // CRYPTO-25: FrameRouter ansprechen
    private fun routeDecryptedMedia(mediaData: ByteArray) {
        com.securecall.app.ghostnet.frame.FrameRouter.route(mediaData)
    }

    // CRYPTO-25: FrameRouter mit entschlüsseltem mediaData aufrufen
    routeDecryptedMedia(mediaData)

    // CRYPTO-26: Structured routing über FrameRouter
    if (parsedFrame2 != null) {
        com.securecall.app.ghostnet.frame.FrameRouter.routeStructured(parsedFrame2)
    }

    // CRYPTO-28: neue Decrypt-Schicht (Session-gebunden)
    private fun sessionDecrypt(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptWithSession(frame)
    }

    // CRYPTO-28: im route(): raw = sessionDecrypt(frame)
    val raw = sessionDecrypt(frame)

    // CRYPTO-33: öffentlich aufrufbare inbound-Methode
    fun handleInboundMediaFrame(frame: MediaFrame) {
        processInboundRaw(frame.data)
    }

    // CRYPTO-34: FrameV1 decrypt in die Pipeline setzen
    private fun decryptFrameV1(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptFrameV1(frame)
    }

    // CRYPTO-34: im inbound-flow → decryptFrameV1 statt raw
    val raw = decryptFrameV1(frame)

    // CRYPTO-37: Header + FrameType aus decrypted Frame extrahieren
    private fun extractHeaderAndType(data: ByteArray): com.securecall.app.ghostnet.frame.FrameType {
        if (data.size < 4) return com.securecall.app.ghostnet.frame.FrameType.UNKNOWN

        val header = com.securecall.app.ghostnet.frame.header.FrameHeaderV1.parse(data)
            ?: return com.securecall.app.ghostnet.frame.FrameType.UNKNOWN

        return com.securecall.app.ghostnet.frame.FrameTypeResolver.resolve(header.flags)
    }

    // CRYPTO-37: structured router hazard
    private fun dispatchByFrameType(type: com.securecall.app.ghostnet.frame.FrameType, data: ByteArray) {
        when (type) {

            com.securecall.app.ghostnet.frame.FrameType.AUDIO -> {
                android.util.Log.d("FRAME_PIPE", "Inbound AUDIO")
                // später: PCM decode → Jitterbuffer → AudioTrack
            }

            com.securecall.app.ghostnet.frame.FrameType.CONTROL -> {
                android.util.Log.d("FRAME_PIPE", "Inbound CONTROL")
                // später: Call Control State Machine
            }

            com.securecall.app.ghostnet.frame.FrameType.KEEPALIVE -> {
                android.util.Log.d("FRAME_PIPE", "Inbound KEEPALIVE")
                // später: Heartbeat + Session Liveness Tracking
            }

            else -> {
                android.util.Log.w("FRAME_PIPE", "Inbound UNKNOWN frame")
            }
        }
    }

    // CRYPTO-37: am Ende der inbound-Pipeline:
    val type = extractHeaderAndType(raw)
    dispatchByFrameType(type, raw)
