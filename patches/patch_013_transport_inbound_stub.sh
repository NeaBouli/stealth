#!/bin/bash
set -e

echo "== patch_013: add TransportThreadInbound stub =="

cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/thread/TransportThreadInbound.kt
package com.securecall.app.ghostnet.transport.thread

import android.util.Log

/**
 * CRYPTO-40 / NET-10:
 * Inbound transport thread (stub).
 *
 * This will later:
 * - read raw frames from the network layer,
 * - hand them to the frame parser,
 * - dispatch decoded frames to the media router.
 *
 * For now it only provides a compilable skeleton.
 */
class TransportThreadInbound : Thread("InboundThread") {

    @Volatile
    private var running = true

    override fun run() {
        Log.d("INBOUND", "TransportThreadInbound RUN (stub)")
        while (running) {
            try {
                // TODO: hook into GhostNet receiver and frame parsing
                sleep(10)
            } catch (t: Throwable) {
                Log.e("INBOUND", "Inbound stub error", t)
            }
        }
        Log.d("INBOUND", "TransportThreadInbound STOP (stub)")
    }

    fun stopThread() {
        running = false
        interrupt()
    }
}
KOT

echo "[OK] Created TransportThreadInbound.kt stub"
echo "== patch_013 done =="
