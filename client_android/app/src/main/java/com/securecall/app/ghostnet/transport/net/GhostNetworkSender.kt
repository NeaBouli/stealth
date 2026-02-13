package com.securecall.app.ghostnet.transport.net

import android.util.Log
import com.securecall.app.ghostnet.transport.EncryptedFrame
import java.util.concurrent.LinkedBlockingQueue

/**
 * CRYPTO-31 / NET-10:
 * Network send stubs for GhostNet.
 */
object GhostNetworkSender {

    private const val TAG = "GHOST_NET_SENDER"

    private val queue = LinkedBlockingQueue<EncryptedFrame>()
    private val outboundQueue = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun enqueue(frame: EncryptedFrame) {
        queue.offer(frame)
        Log.d(TAG, "enqueue(): got frame size=${frame.data.size}")
    }

    fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        worker = Thread {
            Log.d(TAG, "NetworkSender thread started")
            loop()
            Log.d(TAG, "NetworkSender thread stopped")
        }.apply {
            name = "GhostNetworkSender"
            start()
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun loop() {
        while (running) {
            try {
                val frame = queue.poll()
                if (frame != null) {
                    Log.d(TAG, "loop(): would send encrypted frame size=${frame.data.size}")
                }
                Thread.sleep(5)
            } catch (t: Throwable) {
                Log.e(TAG, "loop(): error", t)
            }
        }
    }

    // CRYPTO-40: outbound wire frame queue
    fun enqueueOutbound(data: ByteArray) {
        outboundQueue.offer(data)
    }

    fun dequeueOutbound(): ByteArray? = outboundQueue.poll()

    fun sendRawNetworkFrame(data: ByteArray) {
        try {
            Log.d("OUTBOUND", "stub sendRawNetworkFrame(): ${data.size} bytes (no real network yet)")
        } catch (t: Throwable) {
            Log.e("OUTBOUND", "sendRawNetworkFrame(): failed", t)
        }
    }

    // CRYPTO-40: Outbound FrameV1 helpers
    fun sendAudioFrameV1(pcm: ByteArray) {
        val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
        val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptAudioFrameV1(ctx, pcm)
        enqueueOutbound(frame)
    }

    fun sendControlFrameV1(code: Int, text: String) {
        val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
        val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptControlFrameV1(ctx, code, text)
        enqueueOutbound(frame)
    }

    fun sendKeepAliveFrameV1() {
        val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
        val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptKeepAliveFrameV1(ctx)
        enqueueOutbound(frame)
    }
}
