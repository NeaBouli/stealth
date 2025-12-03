package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.media.MediaFrame

/**
 * CRYPTO-33:
 * Minimaler Inbound-Thread, der aus dem NetworkReceiver Frames zieht
 * und an MediaRouter weitergibt (SessionDecrypt -> MediaDecrypt).
 */
class TransportThreadInbound(
    private val router: (MediaFrame) -> Unit
) : Thread("TransportThreadInbound") {

    @Volatile
    private var running = false

    override fun run() {
        running = true
        Log.d("INBOUND_THREAD", "started")

        while (running) {
            try {
                val enc = com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver.poll()
                if (enc != null) {
                    Log.d("INBOUND_THREAD", "got encrypted inbound frame size=${enc.data.size}")

                    val mf = MediaFrame(enc.data, enc.timestamp)
                    router(mf)
                }

                sleep(5)

            } catch (t: Throwable) {
                Log.e("INBOUND_THREAD", "error", t)
            }
        }

        Log.d("INBOUND_THREAD", "stopped")
    }

    fun stopThread() {
        running = false
        interrupt()
    }
}
