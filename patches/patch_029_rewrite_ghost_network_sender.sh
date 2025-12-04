#!/bin/bash
set -e

echo "== patch_029: normalize GhostNetworkSender + Receiver stubs =="

cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/net/GhostNetworkSender.kt
package com.securecall.app.ghostnet.transport.net

import android.util.Log
import com.securecall.app.ghostnet.transport.EncryptedFrame
import java.util.concurrent.LinkedBlockingQueue

/**
 * CRYPTO-31 / NET-10:
 * Network send/receive stubs for GhostNet.
 *
 * Responsibilities:
 *  - GhostNetworkSender: queue of outbound EncryptedFrame (high level).
 *  - outboundQueue + sendRawNetworkFrame(): raw wire bytes toward network layer.
 *  - GhostNetworkReceiver: simple inbound queue for raw frames (used by TransportThreadInbound).
 *
 * Real WebSocket/QUIC/sRTP wiring will be added later on the platform side.
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
                    // Later: encode to wire format and push into outboundQueue/sendRawNetworkFrame
                    Log.d(TAG, "loop(): would send encrypted frame size=${frame.data.size}")
                }
                Thread.sleep(5)
            } catch (t: Throwable) {
                Log.e(TAG, "loop(): error", t)
            }
        }
    }
}

/**
 * CRYPTO-40:
 * Generic outbound queue for already-encoded wire frames (ByteArray).
 * TransportThreadOutbound will dequeue from here and call sendRawNetworkFrame().
 */
private val outboundQueue = LinkedBlockingQueue<ByteArray>()

fun enqueueOutbound(data: ByteArray) {
    outboundQueue.offer(data)
}

fun dequeueOutbound(): ByteArray? = outboundQueue.poll()

/**
 * CRYPTO-40:
 * Actual network send (stub).
 *
 * For now we only log. Real WebSocket/UDP/QUIC wiring will be provided by
 * a platform-specific network layer that calls into this function.
 */
fun sendRawNetworkFrame(data: ByteArray) {
    try {
        Log.d("OUTBOUND", "stub sendRawNetworkFrame(): ${data.size} bytes (no real network yet)")
    } catch (t: Throwable) {
        Log.e("OUTBOUND", "sendRawNetworkFrame(): failed", t)
    }
}

/**
 * NET-10:
 * Inbound network stub. Platform code should push raw frames here via offerInboundFrame().
 * TransportThreadInbound polls via GhostNetworkReceiver.pollInboundFrame().
 */
object GhostNetworkReceiver {

    private const val TAG = "GHOST_NET_RECEIVER"
    private val inboundQueue = LinkedBlockingQueue<ByteArray>()

    fun offerInboundFrame(raw: ByteArray) {
        inboundQueue.offer(raw)
        Log.d(TAG, "offerInboundFrame(): got inbound raw frame size=${raw.size}")
    }

    fun pollInboundFrame(): ByteArray? = inboundQueue.poll()
}

// CRYPTO-40: Outbound FrameV1 helpers

fun sendAudioFrameV1(pcm: ByteArray) {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptAudioFrameV1(
        ctx,
        pcm
    )
    enqueueOutbound(frame)
}

fun sendControlFrameV1(code: Int, text: String) {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptControlFrameV1(
        ctx,
        code,
        text
    )
    enqueueOutbound(frame)
}

fun sendKeepAliveFrameV1() {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession ?: return
    val frame = com.securecall.app.ghostnet.media.crypto.MediaEncryptor.buildAndEncryptKeepAliveFrameV1(
        ctx
    )
    enqueueOutbound(frame)
}
KOT

echo "[OK] Rewrote GhostNetworkSender.kt to canonical stub version"
echo "== patch_029 done =="
