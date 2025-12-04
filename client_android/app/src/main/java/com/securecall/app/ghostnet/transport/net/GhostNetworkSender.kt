package com.securecall.app.ghostnet.transport.net

import android.util.Log
import com.securecall.app.ghostnet.transport.EncryptedFrame
import java.util.concurrent.LinkedBlockingQueue

/**
 * CRYPTO-31:
 * Stub für die spätere Network-Sendepipeline.
 *
 * Aktuell:
 *  - nimmt EncryptedFrame entgegen
 *  - legt sie in eine Queue
 *  - ein Hintergrund-Thread "würde" sie ins Netz schicken (jetzt nur Log)
 */
object GhostNetworkSender {

    private const val TAG = "GHOST_NET_SENDER"

    private val queue = LinkedBlockingQueue<EncryptedFrame>()
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
        }.apply { name = "GhostNetworkSender"; start() }
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
                    // später: WebSocket / QUIC / SRTP etc.
                    Log.d(TAG, "loop(): would send encrypted frame size=${frame.data.size}")
                }
                Thread.sleep(5)
            } catch (t: Throwable) {
                Log.e(TAG, "loop(): error", t)
            }
        }
    }
}

// CRYPTO-40: Outbound FrameV1 - AUDIO
fun sendAudioFrameV1(pcm: ByteArray) {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptAudioFrameV1(
        ctx, pcm
    )
    enqueueOutbound(frame)
}

// CRYPTO-40: Outbound FrameV1 - CONTROL
fun sendControlFrameV1(code: Int, text: String) {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptControlFrameV1(
        ctx, code, text
    )
    enqueueOutbound(frame)
}

// CRYPTO-40: Outbound FrameV1 - KEEPALIVE
fun sendKeepAliveFrameV1() {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptKeepAliveFrameV1(
        ctx
    )
    enqueueOutbound(frame)
}

// CRYPTO-40: generische Outbound-Queue
private val outboundQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()

fun enqueueOutbound(data: ByteArray) {
    outboundQueue.offer(data)
}

// vom TransportThreadOutbound abgeholt:
fun dequeueOutbound(): ByteArray? {
    return outboundQueue.poll()
}

// CRYPTO-40: tatsächliches Senden über Transport
fun sendRawNetworkFrame(data: ByteArray) {
    try {
        network.send(data)   // hängt von deiner implementierten Netzwerk-Klasse ab
        android.util.Log.d("OUTBOUND", "sent ${data.size} bytes")
    } catch (t: Throwable) {
        android.util.Log.e("OUTBOUND", "sendRawNetworkFrame(): failed", t)
    }
}
