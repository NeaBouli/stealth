package com.securecall.app.ghostnet.transport

import android.util.Log
import com.securecall.app.ghostnet.transport.thread.GhostTransportThread

/**
 * PATCH 230 / 231 / 237 / 251 / 254 + CRYPTO-06..36:
 * Vollständiger GhostNet Transport.
 */
object GhostTransport {

    private var thread: GhostTransportThread? = null
    private var running = false

    // PATCH 251: optionaler CryptoContext
    @Volatile
    private var cryptoContext: com.securecall.app.ghostnet.crypto.SessionCryptoContext? = null

    // CRYPTO-07: Nonce counter
    private var nonceCounter: Long = 0L

    // CRYPTO-30: Queue für verschlüsselte Frames
    private val encryptedQueue =
        java.util.concurrent.LinkedBlockingQueue<EncryptedFrame>()

    // CRYPTO-33: Inbound-Thread Management
    private var inboundThread: com.securecall.app.ghostnet.transport.thread.TransportThreadInbound? = null

    // ===================== Transport Lifecycle =====================

    private fun onTransportStart() {
        notifyCallEstablishing()
    }

    private fun notifyCallEstablishing() {
        Log.d("GHOST_CALL", "notifyCallEstablishing()")
        com.securecall.app.ghostnet.call.GhostCallController.startOutgoingCall()
    }

    fun start() {
        if (running) {
            Log.w("GHOST_TRANSPORT", "start(): already running")
            return
        }
        running = true
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

    // PATCH 231: quiet stop for call termination
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
        stop()
    }

    fun queueSize(): Int {
        return thread?.queueSize() ?: 0
    }

    // DummyAudioRecorder test entry point
    fun enqueueTestFrame(data: ByteArray) {
        Log.d("GHOST_TRANSPORT", "enqueueTestFrame(): size=${data.size}")
    }

    // ===================== CryptoContext =====================

    fun setCryptoContext(ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext) {
        Log.d("GHOST_TRANSPORT", "setCryptoContext(): " + ctx.debugSummary())
        cryptoContext = ctx
    }

    fun getCryptoContext(): com.securecall.app.ghostnet.crypto.SessionCryptoContext? = cryptoContext

    // ===================== Encrypt Helpers =====================

    private fun encryptForSend(payload: ByteArray): ByteArray {
        val ctx = getCryptoContext()
        return com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encrypt(payload, ctx)
    }

    fun debugEncryptDummyFrame() {
        val dummy = ByteArray(32) { 0x42.toByte() }
        Log.d("GHOST_TRANSPORT", "debugEncryptDummyFrame(): before encrypt size=${dummy.size}")
        val enc = encryptForSend(dummy)
        Log.d("GHOST_TRANSPORT", "debugEncryptDummyFrame(): after encrypt size=${enc.size}")
    }

    // CRYPTO-06: key-aware encrypt
    private fun encryptForSendWithKey(payload: ByteArray): ByteArray {
        val ctx = getCryptoContext()
        return com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encryptWithKey(payload, ctx)
    }

    // ===================== CRYPTO-07: FrameHeader + Nonce =====================

    private fun buildHeaderForOutbound(payload: ByteArray): ByteArray {
        nonceCounter += 1
        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonceCounter
        )
        return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.build(header, payload)
    }

    fun debugBuildHeader(payload: ByteArray): ByteArray {
        return buildHeaderForOutbound(payload)
    }

    // CRYPTO-08: Nonce-Manager
    private fun buildHeaderForOutboundNonceManaged(payload: ByteArray): ByteArray {
        val nonce = com.securecall.app.ghostnet.crypto.NonceManager.nextNonce()
        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonce
        )
        return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.build(header, payload)
    }

    fun debugBuildHeaderNonceManaged(payload: ByteArray): ByteArray {
        return buildHeaderForOutboundNonceManaged(payload)
    }

    // CRYPTO-09: nonce debug
    private fun debugLogNonce(nonce: Long) {
        if (com.securecall.app.debug.DebugGuard.allowNoncePrint(nonce)) {
            Log.d("NONCE", "nonce=$nonce")
        }
    }

    private fun debugNonceHook(nonce: Long) {
        debugLogNonce(nonce)
    }

    private fun buildHeaderForOutboundNonceManaged_debugWrap(raw: ByteArray): ByteArray {
        val frame = debugBuildHeaderNonceManaged(raw)
        val (header, _) =
            com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.parse(frame)
        debugNonceHook(header.nonce)
        return frame
    }

    // ===================== CRYPTO-10: CiphertextFrame =====================

    fun buildCiphertextFrameFromMedia(frame: com.securecall.app.ghostnet.media.MediaFrame): com.securecall.app.ghostnet.media.crypto.CiphertextFrame {
        val nonce = com.securecall.app.ghostnet.crypto.NonceManager.nextNonce()
        val header = com.securecall.app.ghostnet.media.crypto.FrameHeader(
            version = 1,
            nonce = nonce
        )
        val ciphertext = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.encrypt(frame)
        try {
            debugNonceHook(nonce)
        } catch (_: Throwable) {}
        return com.securecall.app.ghostnet.media.crypto.CiphertextFrame(
            header = header,
            ciphertext = ciphertext
        )
    }

    fun debugBuildCiphertextFrameDummy(): com.securecall.app.ghostnet.media.crypto.CiphertextFrame {
        val data = ByteArray(64)
        java.util.Random().nextBytes(data)
        val frame = com.securecall.app.ghostnet.media.MediaFrame(data, System.currentTimeMillis())
        return buildCiphertextFrameFromMedia(frame)
    }

    // CRYPTO-11: Wire-Format builder
    fun debugBuildWireFrameDummy(): ByteArray {
        val dummyFrame = debugBuildCiphertextFrameDummy()
        return com.securecall.app.ghostnet.media.crypto.CiphertextWireFormat.toByteArray(dummyFrame)
    }

    // CRYPTO-13: Generate wire frames for replay test
    fun debugGenerateWireFrameListForReplayTest(): ArrayList<ByteArray> {
        val list = ArrayList<ByteArray>()
        repeat(3) {
            val raw = debugBuildWireFrameDummy()
            list.add(raw)
        }
        val replay = list[1]
        list.add(replay)
        val backward = list[0]
        list.add(backward)
        return list
    }

    // ===================== CRYPTO-20+: Outbound Send Methods =====================

    fun sendTestWireFrame(payloadSize: Int = 32) {
        try {
            val payload = ByteArray(payloadSize)
            java.util.Random().nextBytes(payload)
            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payload)
            Log.d("OUT_WIRE", "sendTestWireFrame(): encodedSize=${raw.size}, nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.processInboundRaw(raw)
        } catch (t: Throwable) {
            Log.e("OUT_WIRE", "sendTestWireFrame error", t)
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
            Log.d("OUT_AUDIO", "sendAudioFrame(): pcm=${pcm.size}, payload=${payload.size}, nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.processInboundRaw(raw)
        } catch (t: Throwable) {
            Log.e("OUT_AUDIO", "Error sending AudioFrame", t)
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
            Log.d("OUT_CTRL", "sendControlFrame(): code=$code info=$info nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.processInboundRaw(raw)
        } catch (t: Throwable) {
            Log.e("OUT_CTRL", "Error sending ControlFrame", t)
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
            Log.d("OUT_KEEPALIVE", "sendKeepAlive(): nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.processInboundRaw(raw)
        } catch (t: Throwable) {
            Log.e("OUT_KEEPALIVE", "Error sending KeepAliveFrame", t)
        }
    }

    // CRYPTO-23: AudioFrame über WireCryptoStub
    fun sendAudioFrameWithCryptoStub(pcm: ByteArray) {
        try {
            val frame = com.securecall.app.ghostnet.frame.AudioFrame(pcm)
            val payloadPlain = com.securecall.app.ghostnet.frame.FrameSerializer.encodeAudio(frame)
            val payloadEnc = com.securecall.app.ghostnet.crypto.WireCryptoStub.encryptPayload(payloadPlain)
            val nonce = System.currentTimeMillis()
            val header = com.securecall.app.ghostnet.crypto.header.WireEncoder.buildHeaderWithNonce(nonce)
            val raw = com.securecall.app.ghostnet.crypto.header.WireEncoder.encode(header, payloadEnc)
            Log.d("OUT_AUDIO_CRYPTO",
                "sendAudioFrameWithCryptoStub(): pcm=${pcm.size}, plain=${payloadPlain.size}, enc=${payloadEnc.size}, nonce=$nonce")
            com.securecall.app.ghostnet.media.GhostMediaRouter.processInboundRaw(raw)
        } catch (t: Throwable) {
            Log.e("OUT_AUDIO_CRYPTO", "Error sending AudioFrame via WireCryptoStub", t)
        }
    }

    // ===================== CRYPTO-29+: Encrypted Frame Pipeline =====================

    fun enqueueEncryptedFrameFromPcm(pcm: ByteArray) {
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)
        Log.d("GHOST_TRANSPORT", "enqueueEncryptedFrameFromPcm(): pcmSize=${pcm.size}, encSize=${encrypted.size}")
    }

    fun enqueueEncryptedFrame(frame: EncryptedFrame) {
        encryptedQueue.offer(frame)
        Log.d("GHOST_TRANSPORT", "enqueueEncryptedFrame(): size=${frame.data.size}")
    }

    fun dequeueEncryptedFrame(): EncryptedFrame? {
        return encryptedQueue.poll()
    }

    fun sendPcm(pcm: ByteArray) {
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)
        val frame = EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
    }

    // CRYPTO-31: EncryptedFrame an NetworkSender weiterreichen
    fun forwardEncryptedToNetwork(frame: EncryptedFrame) {
        com.securecall.app.ghostnet.transport.net.GhostNetworkSender.enqueue(frame)
    }

    // CRYPTO-32: PCM → encrypt → queue → network
    fun sendPcmWithNetwork(pcm: ByteArray) {
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptPcm(pcm)
        val frame = EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    // ===================== CRYPTO-33: Inbound Pipeline =====================

    fun startInboundPipeline() {
        if (inboundThread != null) return
        inboundThread =
            com.securecall.app.ghostnet.transport.thread.TransportThreadInbound { frame ->
                com.securecall.app.ghostnet.media.GhostMediaRouter.handleInboundMediaFrame(frame)
            }.apply { start() }
        Log.d("GHOST_TRANSPORT", "Inbound pipeline STARTED")
    }

    fun stopInboundPipeline() {
        inboundThread?.stopThread()
        inboundThread = null
        Log.d("GHOST_TRANSPORT", "Inbound pipeline STOPPED")
    }

    // ===================== CRYPTO-36: FrameV1 Methods =====================

    fun sendAudioFrameV1(data: ByteArray) {
        val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession
        if (ctx == null) {
            Log.e("GHOST_TRANSPORT", "sendAudioFrameV1(): no active session")
            return
        }
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptAudioFrameV1(data)
        val frame = EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    fun sendControlFrameV1(code: Int, message: String) {
        val body = ("$code:$message").toByteArray()
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptControlFrameV1(body)
        val frame = EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }

    fun sendKeepAliveFrameV1() {
        val body = byteArrayOf(0x00)
        val encrypted = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
            .encryptKeepAliveFrameV1(body)
        val frame = EncryptedFrame(encrypted)
        enqueueEncryptedFrame(frame)
        forwardEncryptedToNetwork(frame)
    }
}
