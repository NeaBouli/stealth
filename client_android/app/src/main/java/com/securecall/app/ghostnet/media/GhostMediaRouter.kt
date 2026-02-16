package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * BACKEND-63..66 / PATCH 211..239 / CRYPTO-06..38:
 * Zentraler Media-Router: decrypt → decode → playback.
 */
object GhostMediaRouter {

    private const val TAG = "MEDIA_ROUTER"

    // PATCH 224: decoder context
    private val decoderCtx = com.securecall.app.ghostnet.media.DecoderContext()

    // PATCH 226: AudioPlayer
    private val audioPlayer =
        com.securecall.app.ghostnet.media.playback.GhostAudioPlayer(48000, 1)

    // PATCH 251: CryptoContext
    private var cryptoContext: com.securecall.app.ghostnet.crypto.SessionCryptoContext? = null

    // Singleton accessor (object is already singleton, but callers use .get())
    fun get(): GhostMediaRouter = this

    // ===================== Main Routing Entry Points =====================

    fun route(frame: MediaFrame) {
        Log.d(TAG, "route(): MediaFrame size=${frame.data.size}")
        handle(frame)
    }

    // CRYPTO-33: public inbound method
    fun handleInboundMediaFrame(frame: MediaFrame) {
        processInboundRaw(frame.data)
    }

    // ===================== Core Pipeline =====================

    // PATCH 211: decrypt → decode → playback
    private fun handle(frame: MediaFrame) {
        val raw = decrypt(frame)
        val pcm = safeDecode(raw) ?: return
        playPcm(pcm)
    }

    // BACKEND-64: Decrypt via MediaDecryptor
    private fun decrypt(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decrypt(frame)
    }

    // PATCH 239: decode via DummyDecoder
    private fun decode(raw: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decoder.DummyDecoder.decode(raw)
    }

    // PATCH 230: safe decode with error handling
    private fun safeDecode(raw: ByteArray): ShortArray? {
        return try {
            decode(raw)
        } catch (t: Throwable) {
            handleDecodeError(t)
            null
        }
    }

    // PATCH 226: playback via audioPlayer
    private fun playPcm(pcm: ShortArray) {
        audioPlayer.write(pcm)
    }

    // PATCH 211: ByteArray → ShortArray Konverter
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

    // ===================== Alternative Decrypt Methods =====================

    private fun decryptFrame(frame: MediaFrame): MediaFrame {
        return com.securecall.app.ghostnet.media.crypto.MediaDecrypt.decrypt(frame)
    }

    // CRYPTO-06: key-aware decrypt
    private fun decryptWithKey(frame: MediaFrame): MediaFrame {
        val ctx = cryptoContext
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptWithKey(frame, ctx)
    }

    // CRYPTO-28: session-bound decrypt
    private fun sessionDecrypt(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptWithSession(frame)
    }

    // CRYPTO-34: FrameV1 decrypt
    private fun decryptFrameV1(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decryptFrameV1(frame)
    }

    // CRYPTO-24: Wire-level decrypt — uses real AEAD via SessionCipherEngine
    private fun decryptWirePayload(cipher: ByteArray): ByteArray {
        return try {
            val binding = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            val session = binding.activeSession
            if (session != null) {
                com.securecall.app.ghostnet.crypto.SessionCipherEngine.decrypt(session, cipher)
            } else {
                Log.e("WIRE_CRYPTO", "decryptWirePayload(): no active session")
                cipher
            }
        } catch (t: Throwable) {
            Log.e("WIRE_CRYPTO", "decryptWirePayload() error", t)
            cipher
        }
    }

    // CRYPTO-24: Media-level decrypt
    private fun decryptMediaFrame(cipherData: ByteArray): ByteArray {
        val dummyFrame = MediaFrame(
            data = cipherData,
            timestamp = System.currentTimeMillis()
        )
        return decrypt(dummyFrame)
    }

    // ===================== Alternative Decode Methods =====================

    // BACKEND-65: decode via MediaDecoder
    private fun decodeAudio(bytes: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decode.MediaDecoder.decode(bytes)
    }

    // PATCH 214: decode via AudioDecoder codec
    private fun decodeViaCodec(raw: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decoder.AudioDecoder.decode(raw)
    }

    // ===================== Error Handling =====================

    // PATCH 230: error hook
    private fun handleDecodeError(t: Throwable) {
        Log.e("GHOST_CALL", "MediaRouter decode error", t)
        com.securecall.app.ghostnet.call.GhostCallController.terminateCall()
    }

    // ===================== Decoder Management =====================

    // PATCH 224: ensure decoder
    private fun ensureDecoder() {
        decoderCtx.prepareNativeDecoder(48000, 1)
    }

    fun resetDecoderStub() {
        decoderCtx.freeNativeDecoder()
        Log.d(TAG, "Decoder Stub Reset")
        ensureDecoder()
    }

    // PATCH 225: reset full media pipeline
    fun resetPipelineStub() {
        Log.d(TAG, "resetPipelineStub(): full reset")
        decoderCtx.freeNativeDecoder()
        ensureDecoder()
    }

    // ===================== Playback Helpers =====================

    // PATCH 226: debug beep passthrough
    fun testBeep(pcm: ShortArray) {
        playPcm(pcm)
    }

    // PATCH 227: decoded pcm handler
    private fun handleDecodedPcm(pcm: ShortArray) {
        playPcm(pcm)
    }

    private fun logFlowEvent(stage: String) {
        Log.d("MEDIA_FLOW", "stage=$stage")
    }

    // ===================== Lifecycle =====================

    // PATCH 231: graceful shutdown of media layer
    fun quietShutdown() {
        Log.d(TAG, "quietShutdown(): stopping media")
        audioPlayer.stop()
        audioPlayer.release()
        decoderCtx.freeNativeDecoder()
    }

    // PATCH 237: media reset stub
    fun resetMedia() {
        Log.w(TAG, "resetMedia(): future decoder cleanup here")
    }

    // ===================== CryptoContext =====================

    fun attachCryptoContext(ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext) {
        Log.d(TAG, "attachCryptoContext(): " + ctx.debugSummary())
        cryptoContext = ctx
    }

    fun getCryptoContext(): com.securecall.app.ghostnet.crypto.SessionCryptoContext? = cryptoContext

    fun debugDecryptWithKey(frame: MediaFrame) {
        decryptWithKey(frame)
    }

    // ===================== CRYPTO-07: Header Parsing =====================

    fun parseInboundFrame(raw: ByteArray): EncryptedFrame {
        val (header, payload) =
            com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.parse(raw)
        return EncryptedFrame(
            version = header.version,
            nonce = header.nonce,
            ciphertext = payload
        )
    }

    fun debugParseInbound(raw: ByteArray) {
        val f = parseInboundFrame(raw)
        Log.d(TAG,
            "Parsed header: version=${f.version}, nonce=${f.nonce}, ciphertextLen=${f.ciphertext.size}")
    }

    // ===================== CRYPTO-10+: Debug Methods =====================

    fun debugInspectCiphertextFrame(frame: com.securecall.app.ghostnet.media.crypto.CiphertextFrame) {
        val h = frame.header
        val size = frame.ciphertext.size
        Log.d(TAG, "CiphertextFrame: version=${h.version}, nonce=${h.nonce}, size=$size")
    }

    fun debugParseWireFrame(raw: ByteArray) {
        try {
            val cf = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            Log.d("WIRE_PARSE",
                "Parsed WireFrame: version=${cf.header.version}, nonce=${cf.header.nonce}, cipherLen=${cf.ciphertext.size}")
        } catch (t: Throwable) {
            Log.e("WIRE_PARSE", "Error parsing WireFrame", t)
        }
    }

    fun debugValidateWireFrame(raw: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            val ok = com.securecall.app.ghostnet.media.crypto.CiphertextValidator.isValid(frame)
            Log.d("WIRE_VALIDATE",
                "WireFrame validation result = $ok (version=${frame.header.version}, nonce=${frame.header.nonce}, len=${frame.ciphertext.size})")
        } catch (t: Throwable) {
            Log.e("WIRE_VALIDATE", "Exception while validating WireFrame", t)
        }
    }

    // CRYPTO-13: Replay Detection
    private fun detectReplay(nonce: Long) {
        com.securecall.app.ghostnet.crypto.ReplayDetector.check(nonce)
    }

    fun debugParseWireFrame_withReplay(raw: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            detectReplay(frame.header.nonce)
            Log.d("WIRE_PARSE",
                "Parsed WF (with replay-check): version=${frame.header.version}, nonce=${frame.header.nonce}, len=${frame.ciphertext.size}")
        } catch (t: Throwable) {
            Log.e("WIRE_PARSE", "Replay-Parse Error", t)
        }
    }

    fun debugParseWireFrame_withSecurity(raw: ByteArray) {
        try {
            val f = com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.fromByteArray(raw)
            com.securecall.app.ghostnet.crypto.ReplayDetector.checkWithSecurity(f.header.nonce)
            Log.d("WIRE_SEC", "Parsed with Security: version=${f.header.version}, nonce=${f.header.nonce}")
        } catch (t: Throwable) {
            Log.e("WIRE_SEC", "Security parse error", t)
        }
    }

    fun debugParseWireHeader(raw: ByteArray) {
        try {
            val header = com.securecall.app.ghostnet.crypto.header.WireHeaderParser.parse(raw)
            if (header != null) {
                Log.d("WIRE_HDR",
                    "Parsed Header: version=${header.version}, nonce=${header.nonce}")
            }
        } catch (t: Throwable) {
            Log.e("WIRE_HDR", "Header parse error", t)
        }
    }

    // ===================== CRYPTO-18: Inbound Pipeline =====================

    fun processInboundRaw(raw: ByteArray) {
        try {
            // 1) Header parsen
            val header = com.securecall.app.ghostnet.crypto.header.WireHeaderParser.parse(raw)
            if (header == null) {
                Log.w("MEDIA_PIPE", "Header invalid, continuing anyway (debug phase)")
                return
            }

            // 2) ReplayDetector prüfen
            com.securecall.app.ghostnet.crypto.ReplayDetector.check(header.nonce)

            // 3) Wire-level decrypt
            val cipherData = decryptWirePayload(raw)

            // 4) Media-level decrypt
            val mediaData = decryptMediaFrame(cipherData)

            // 5) Parse frame types
            val parsedFrame = parseFrameTypes(mediaData)
            if (parsedFrame != null) {
                Log.d("FRAME_PIPE", "Frame after WireCrypto + MediaDecrypt: $parsedFrame")
            }

            // 6) Route decrypted media
            routeDecryptedMedia(mediaData)

            // 7) Structured routing
            if (parsedFrame != null) {
                com.securecall.app.ghostnet.frame.FrameRouter.routeStructured(parsedFrame)
            }

            // 8) Extract header and type, dispatch
            val type = extractHeaderAndType(mediaData)
            dispatchByFrameType(type, mediaData)

            // 9) Extract and parse body
            val body = extractBodyV1(mediaData)
            val parsedBody = parseBody(type, body)
            if (parsedBody != null) {
                Log.d("FRAME_PIPE", "ParsedBody = $parsedBody")
            }

            // 10) Decode and play
            val pcm = safeDecode(mediaData) ?: return
            playPcm(pcm)

        } catch (t: Throwable) {
            Log.e("MEDIA_PIPE", "Exception in processInboundRaw()", t)
        }
    }

    // ===================== CRYPTO-19: Debug Roundtrip =====================

    fun debugEncodeRoundtrip(payloadSize: Int = 32) {
        try {
            val payload = ByteArray(payloadSize)
            kotlin.random.Random.Default.nextBytes(payload)
            val nonce = com.securecall.app.ghostnet.crypto.NonceManager.nextNonce()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .buildHeaderWithNonce(nonce)
            val encoded = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .encode(header, payload)
            Log.d("WIRE_RT", "Encoded frame: size=${encoded.size}, nonce=$nonce")
            debugParseWireHeader(encoded)
            processInboundRaw(encoded)
        } catch (t: Throwable) {
            Log.e("WIRE_RT", "Error in debugEncodeRoundtrip()", t)
        }
    }

    // ===================== CRYPTO-22+: Frame Parsing =====================

    private fun parseFrameTypes(raw: ByteArray): Any? {
        return try {
            com.securecall.app.ghostnet.frame.FrameParser.parse(raw)
        } catch (t: Throwable) {
            Log.e("FRAME_PARSE", "FrameType parse error", t)
            null
        }
    }

    // CRYPTO-25: route to FrameRouter
    private fun routeDecryptedMedia(mediaData: ByteArray) {
        com.securecall.app.ghostnet.frame.FrameRouter.route(mediaData)
    }

    // ===================== CRYPTO-37: FrameType Dispatch =====================

    private fun extractHeaderAndType(data: ByteArray): com.securecall.app.ghostnet.frame.FrameType {
        if (data.size < 4) return com.securecall.app.ghostnet.frame.FrameType.UNKNOWN

        val header = com.securecall.app.ghostnet.frame.header.FrameHeaderV1.parse(data)
            ?: return com.securecall.app.ghostnet.frame.FrameType.UNKNOWN

        return com.securecall.app.ghostnet.frame.FrameTypeResolver.resolve(header.flags)
    }

    private fun dispatchByFrameType(type: com.securecall.app.ghostnet.frame.FrameType, data: ByteArray) {
        when (type) {
            com.securecall.app.ghostnet.frame.FrameType.AUDIO -> {
                Log.d("FRAME_PIPE", "Inbound AUDIO")
            }
            com.securecall.app.ghostnet.frame.FrameType.CONTROL -> {
                Log.d("FRAME_PIPE", "Inbound CONTROL")
            }
            com.securecall.app.ghostnet.frame.FrameType.KEEPALIVE -> {
                Log.d("FRAME_PIPE", "Inbound KEEPALIVE")
            }
            else -> {
                Log.w("FRAME_PIPE", "Inbound UNKNOWN frame")
            }
        }
    }

    // ===================== CRYPTO-38: Body Parsing =====================

    private fun extractBodyV1(data: ByteArray): ByteArray {
        if (data.size <= 4) return ByteArray(0)
        return data.copyOfRange(4, data.size)
    }

    private fun parseBody(type: com.securecall.app.ghostnet.frame.FrameType, body: ByteArray): Any? {
        return com.securecall.app.ghostnet.frame.body.FrameBodyParser.parse(type, body)
    }
}
