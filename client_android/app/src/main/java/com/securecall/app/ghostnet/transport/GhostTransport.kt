package com.securecall.app.ghostnet.transport

import android.util.Log
import com.securecall.app.ghostnet.transport.thread.GhostTransportThread

/**
 * CLEAN VERSION — PATCH 230 FINAL
 * Vollständiger Fix: Duplikate entfernt, notifyCallEstablishing korrekt,
 * start()-Integration korrekt, onTransportStart korrekt.
 */
object GhostTransport {

    private var thread: GhostTransportThread? = null
    private var running = false

    // =============================================================
    // PATCH 230 FIX: Call Establishing + Start Hooks
    // =============================================================

    /** Called inside start(): announces that transport is starting */
    private fun onTransportStart() {
        notifyCallEstablishing()
    }

    /** Tells CallController that call is establishing */
    private fun notifyCallEstablishing() {
        Log.d("GHOST_CALL", "notifyCallEstablishing()")
        com.securecall.app.ghostnet.call.GhostCallController.startOutgoingCall()
    }

    // =============================================================
    // Transport API
    // =============================================================

    fun start() {
        if (running) {
            Log.w("GHOST_TRANSPORT", "start(): already running")
            return
        }

        running = true

        // PATCH 230 FIX: Correct hook
        onTransportStart()

        Log.d("GHOST_TRANSPORT", "Transport STARTED")

        thread = GhostTransportThread()
        thread?.start()
    }

    fun stop() {
        if (!running) return

        running = false
        thread?.stopThread()
        thread = null

        Log.d("GHOST_TRANSPORT", "Transport STOPPED")
    }

    // Queue inspection (used by debug buttons)
    fun queueSize(): Int {
        return thread?.queueSize() ?: 0
    }
}

    // PATCH 231 — quiet stop for call termination
    fun quietStop() {
        if (!running) return

        Log.d("GHOST_TRANSPORT", "quietStop(): starting graceful shutdown")

        thread?.gracefulStop()
        thread = null

        running = false
    }

// PATCH 237: full transport reset
fun resetTransport() {
    Log.w("GHOST_TRANSPORT", "resetTransport(): stopping thread + clearing state")

    stop() // ordentlich stoppen (Thread halt + cleanup)

    // future: queue.clear(), pipeline flags etc.
}

    // PATCH 251: optionaler CryptoContext für Transport
    @Volatile
    private var cryptoContext: com.securecall.app.ghostnet.crypto.SessionCryptoContext? = null

    fun setCryptoContext(ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext) {
        android.util.Log.d("GHOST_TRANSPORT", "setCryptoContext(): " + ctx.debugSummary())
        cryptoContext = ctx
    }

    fun getCryptoContext(): com.securecall.app.ghostnet.crypto.SessionCryptoContext? = cryptoContext

    // PATCH 254: Helper für ausgehende Frames (noch nicht in Pipeline verdrahtet)
    private fun encryptForSend(payload: ByteArray): ByteArray {
        val ctx = getCryptoContext()
        return com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encrypt(payload, ctx)
    }

    // PATCH 254: Debug-Hook – simulierter Outbound-Frame durch Encryptor
    fun debugEncryptDummyFrame() {
        val dummy = ByteArray(32) { 0x42.toByte() }
        android.util.Log.d("GHOST_TRANSPORT", "debugEncryptDummyFrame(): before encrypt size=${dummy.size}")
        val enc = encryptForSend(dummy)
        android.util.Log.d("GHOST_TRANSPORT", "debugEncryptDummyFrame(): after encrypt size=${enc.size}")
    }

    // CRYPTO-06: integrate key-aware encrypt
    private fun encryptForSendWithKey(payload: ByteArray): ByteArray {
        val ctx = getCryptoContext()
        return com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encryptWithKey(payload, ctx)
    }

    // CRYPTO-07: FrameHeader + Nonce Builder
    private var nonceCounter: Long = 0L

    private fun buildHeaderForOutbound(payload: ByteArray): ByteArray {
        nonceCounter += 1
        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonceCounter
        )

        return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.build(header, payload)
    }

    // CRYPTO-07: Public Debug Entry
    fun debugBuildHeader(payload: ByteArray): ByteArray {
        return buildHeaderForOutbound(payload)
    }

    // CRYPTO-08: zentraler Nonce-Manager für Outbound-Frames
    private fun buildHeaderForOutboundNonceManaged(payload: ByteArray): ByteArray {
        val nonce = com.securecall.app.ghostnet.crypto.NonceManager.nextNonce()

        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonce
        )

        return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.build(header, payload)
    }

    // CRYPTO-08: Debug-Entry, um Nonce-Manager zu testen
    fun debugBuildHeaderNonceManaged(payload: ByteArray): ByteArray {
        return buildHeaderForOutboundNonceManaged(payload)
    }

    // CRYPTO-09: print nonce only if guard allows it
    private fun debugLogNonce(nonce: Long) {
        if (com.securecall.app.debug.DebugGuard.allowNoncePrint(nonce)) {
            android.util.Log.d("NONCE", "nonce=$nonce")
        }
    }

    // CRYPTO-09: Nonce debug hook
    private fun debugNonceHook(nonce: Long) {
        debugLogNonce(nonce)
    }

    // CRYPTO-09: inside nonce-managed header building
    private fun buildHeaderForOutboundNonceManaged_debugWrap(raw: ByteArray): ByteArray {
        val frame = debugBuildHeaderNonceManaged(raw)
        val (header, _) =
            com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.parse(frame)
        debugNonceHook(header.nonce)
        return frame
    }

    // CRYPTO-10: CiphertextFrame über NonceManager + MediaEncryptor bauen
    fun buildCiphertextFrameFromMedia(frame: com.securecall.app.ghostnet.media.MediaFrame): com.securecall.app.ghostnet.media.crypto.CiphertextFrame {
        val nonce = com.securecall.app.ghostnet.crypto.NonceManager.nextNonce()

        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonce
        )

        val ciphertext = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encrypt(frame)

        // Debug: Nonce-Ausgabe (über vorhandene Debug-Hooks)
        try {
            debugNonceHook(nonce)
        } catch (_: Throwable) {
            // falls Debug-Hook noch nicht existiert / später ergänzt wird: silent ignore
        }

        return com.securecall.app.ghostnet.media.crypto.CiphertextFrame(
            header = header,
            ciphertext = ciphertext
        )
    }

    // CRYPTO-10: Debug-Helfer – Dummy-MediaFrame in CiphertextFrame konvertieren
    fun debugBuildCiphertextFrameDummy(): com.securecall.app.ghostnet.media.crypto.CiphertextFrame {
        val data = ByteArray(64)
        java.util.Random().nextBytes(data)
        val frame = com.securecall.app.ghostnet.media.MediaFrame(data, System.currentTimeMillis())
        return buildCiphertextFrameFromMedia(frame)
    }

    // CRYPTO-11: Debug Wire-Format builder
    fun debugBuildWireFrameDummy(): ByteArray {
        val dummyFrame = debugBuildCiphertextFrameDummy()
        return com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.toByteArray(dummyFrame)
    }

    // CRYPTO-13: Generate a list of raw WireFrames for replay testing
    fun debugGenerateWireFrameListForReplayTest(): ArrayList<ByteArray> {
        val list = ArrayList<ByteArray>()

        // 3 nonces forward
        repeat(3) {
            val raw = debugBuildWireFrameDummy()
            list.add(raw)
        }

        // 1 replay (duplicate)
        val replay = list[1]
        list.add(replay)

        // 1 backward (nonce from first)
        val backward = list[0]
        list.add(backward)

        return list
    }

    // CRYPTO-20: Outbound Test-WireFrame bauen und an MediaRouter zurückschicken
    fun sendTestWireFrame(payloadSize: Int = 32) {
        try {
            // 1) Dummy-Payload erzeugen
            val payload = ByteArray(payloadSize)
            java.util.Random().nextBytes(payload)

            // 2) Nonce = System-Zeit
            val nonce = System.currentTimeMillis()

            // 3) Header bauen
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .buildHeaderWithNonce(nonce)

            // 4) WireFrame encoden
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder
                .encode(header, payload)

            android.util.Log.d(
                "OUT_WIRE",
                "sendTestWireFrame(): encodedSize=${raw.size}, nonce=$nonce"
            )

            // 5) Für Debug – sofort zurück in die Inbound-Pipeline (Loopback)
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE
                .processInboundRaw(raw)

        } catch (t: Throwable) {
            android.util.Log.e("OUT_WIRE", "sendTestWireFrame error", t)
        }
    }

    // CRYPTO-22: Outbound AudioFrame
    fun sendAudioFrame(pcm: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.frame.AudioFrame(pcm)
            val payload = com.securecall.app.ghostnet.frame.FrameSerializer.encodeAudio(frame)

            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payload)

            android.util.Log.d("OUT_AUDIO", "sendAudioFrame(): pcm=${pcm.size}, payload=${payload.size}, nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.processInboundRaw(raw)
        } catch (t: Throwable) {
            android.util.Log.e("OUT_AUDIO", "Error sending AudioFrame", t)
        }
    }

    // CRYPTO-22: Outbound ControlFrame
    fun sendControlFrame(code: Int, info: String = "") {
        try {
            val frame = com.securecall.app.ghostnet.frame.ControlFrame(code, info)
            val payload = com.securecall.app.ghostnet.frame.FrameSerializer.encodeControl(frame)

            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payload)

            android.util.Log.d("OUT_CTRL", "sendControlFrame(): code=$code info=$info nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.processInboundRaw(raw)
        } catch (t: Throwable) {
            android.util.Log.e("OUT_CTRL", "Error sending ControlFrame", t)
        }
    }

    // CRYPTO-22: Outbound KeepAliveFrame
    fun sendKeepAlive() {
        try {
            val frame = com.securecall.app.ghostnet.frame.KeepAliveFrame()
            val payload = com.securecall.app.ghostnet.frame.FrameSerializer.encodeKeepAlive(frame)

            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payload)

            android.util.Log.d("OUT_KEEPALIVE", "sendKeepAlive(): nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.processInboundRaw(raw)
        } catch (t: Throwable) {
            android.util.Log.e("OUT_KEEPALIVE", "Error sending KeepAliveFrame", t)
        }
    }

    // CRYPTO-23: Debug-Variante – AudioFrame über WireCryptoStub "verschlüsseln"
    fun sendAudioFrameWithCryptoStub(pcm: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.frame.AudioFrame(pcm)
            val payloadPlain = com.securecall.app.ghostnet.frame.FrameSerializer.encodeAudio(frame)

            // Platzhalter-"Verschlüsselung"
            val payloadEnc = com.securecall.app.ghostnet.crypto.WireCryptoStub.encryptPayload(payloadPlain)

            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payloadEnc)

            android.util.Log.d(
                "OUT_AUDIO_CRYPTO",
                "sendAudioFrameWithCryptoStub(): pcm=${pcm.size}, plain=${payloadPlain.size}, enc=${payloadEnc.size}, nonce=$nonce"
            )

            // Debug: Loopback in die Inbound-Pipeline
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.processInboundRaw(raw)
        } catch (t: Throwable) {
            android.util.Log.e("OUT_AUDIO_CRYPTO", "Error sending AudioFrame via WireCryptoStub", t)
        }
    }

    // CRYPTO-29: erste Stub-Methode für PCM -> Encrypt -> Transport
    fun enqueueEncryptedFrameFromPcm(pcm: ByteArray) {
        // 1) PCM verschlüsseln (über SessionCipherBinding + Engine-Stub)
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)

        // 2) Aktuell nur Logging – spätere Version: in die echte Frame-Queue schieben
        android.util.Log.d(
            "GHOST_TRANSPORT",
            "enqueueEncryptedFrameFromPcm(): pcmSize=${pcm.size}, encSize=${encrypted.size}"
        )

        // TODO (später):
        // queue.enqueue(encrypted)
    }

    // CRYPTO-30: neue Queue für verschlüsselte Frames
    private val encryptedQueue =
        java.util.concurrent.LinkedBlockingQueue<com.securecall.app.ghostnet.transport.EncryptedFrame>()

    fun enqueueEncryptedFrame(frame: com.securecall.app.ghostnet.transport.EncryptedFrame) {
        encryptedQueue.offer(frame)
        android.util.Log.d("GHOST_TRANSPORT", "enqueueEncryptedFrame(): size=${frame.data.size}")
    }

    fun dequeueEncryptedFrame(): com.securecall.app.ghostnet.transport.EncryptedFrame? {
        return encryptedQueue.poll()
    }

    // CRYPTO-30: PCM end-to-end in die verschlüsselte Queue stecken
    fun sendPcm(pcm: ByteArray) {
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)
        val frame = com.securecall.app.ghostnet.transport.EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
    }

    // CRYPTO-31: EncryptedFrame zusätzlich an NetworkSender-Stub weiterreichen
    fun forwardEncryptedToNetwork(frame: com.securecall.app.ghostnet.transport.EncryptedFrame) {
        com.securecall.app.ghostnet.transport.net.GhostNetworkSender.enqueue(frame)
    }

    // CRYPTO-32: Nach Queue auch an Network-Sender geben
    fun sendPcmWithNetwork(pcm: ByteArray) {
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)
        val frame = com.securecall.app.ghostnet.transport.EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    // CRYPTO-33: Inbound-Thread Management
    private var inboundThread: com.securecall.app.ghostnet.transport.thread.TransportThreadInbound? = null

    fun startInboundPipeline() {
        if (inboundThread != null) return
        inboundThread =
            com.securecall.app.ghostnet.transport.thread.TransportThreadInbound { frame ->
                com.securecall.app.ghostnet.media.GhostMediaRouter.get().handleInboundMediaFrame(frame)
            }.apply { start() }
        android.util.Log.d("GHOST_TRANSPORT", "Inbound pipeline STARTED")
    }

    fun stopInboundPipeline() {
        inboundThread?.stopThread()
        inboundThread = null
        android.util.Log.d("GHOST_TRANSPORT", "Inbound pipeline STOPPED")
    }

    // CRYPTO-36: AudioFrame → FrameHeaderV1 → EncryptedFrameQueue → NetworkSender
    fun sendAudioFrameV1(data: ByteArray) {
        val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession
        if (ctx == null) {
            android.util.Log.e("GHOST_TRANSPORT", "sendAudioFrameV1(): no active session")
            return
        }

        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptAudioFrameV1(data)

        val frame = com.securecall.app.ghostnet.transport.EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    // CRYPTO-36: ControlFrame → FrameHeaderV1
    fun sendControlFrameV1(code: Int, message: String) {
        val body = ("$code:$message").toByteArray()
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptControlFrameV1(body)

        val frame = com.securecall.app.ghostnet.transport.EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    // CRYPTO-36: KeepAliveFrame → FrameHeaderV1
    fun sendKeepAliveFrameV1() {
        val body = byteArrayOf(0x00)  // minimal payload
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptKeepAliveFrameV1(body)

        val frame = com.securecall.app.ghostnet.transport.EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }
