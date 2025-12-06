#!/bin/bash
set -e

echo "== patch_039: add Android inbound audio debug doc =="

mkdir -p docs/tech

cat <<'DOC' > docs/tech/ANDROID_INBOUND_AUDIO_DEBUG.md
# Android Inbound Audio Debug Baseline (MainDev Device RF8N313QMFL)

This document describes the current **inbound audio debug path** on Android
and how other developers can plug their own decoding functions into it.

The goal is:

- deterministic, reproducible audio tests on the main dev’s phone,
- a single, well-defined PCM ingress point,
- minimal friction for external contributors.

---

## 1. Device & Environment

Main dev test device:

- Android phone with ADB id: `RF8N313QMFL`
- Builds are done on macOS via CLI (old Intel Mac, no reliable emulator).

Repo layout (relevant parts):

- `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
- `client_android/app/src/main/java/com/securecall/app/ghostnet/media/MediaRouterInboundStub.kt`
- `client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.kt`
- `tools/android_beep_test.sh`

---

## 2. Entry Point for Decoded Audio

All decoded inbound audio should eventually flow through:

```java
com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(byte[] pcm)
Assumptions for pcm:

16-bit signed PCM

mono

little endian

48 kHz sample rate

Any decoder (Rust, C, Java, Kotlin, etc.) that can produce such a byte[]
can be wired in by calling this function.

3. Current UI Behaviour (Beep Test)
In MainActivity there are two buttons:

Start Call

Settings

The Settings button currently acts as an inbound audio test trigger:

java
Code kopieren
btnSettings.setOnClickListener(v -> {
    // Generate short 440 Hz sine beep and push through inbound audio pipeline
    final int sampleRate = 48000;
    final int durationMs = 250;
    final double freqHz = 440.0;

    int numSamples = sampleRate * durationMs / 1000;
    byte[] pcm = new byte[numSamples * 2]; // 16-bit mono little-endian

    for (int i = 0; i < numSamples; i++) {
        double t = (double) i / (double) sampleRate;
        double sample = Math.sin(2.0 * Math.PI * freqHz * t);
        short s = (short) (sample * 32767.0);

        int idx = i * 2;
        pcm[idx] = (byte) (s & 0xFF);
        pcm[idx + 1] = (byte) ((s >> 8) & 0xFF);
    }

    com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(pcm);

    // Optional: still open settings screen
    startActivity(new Intent(this, SettingsActivity.class));
});
When the dev taps Settings, the app:

Generates a 440 Hz sine wave (approx. 250 ms).

Sends it to MediaRouterInboundStub.handleDecodedPcm(pcm).

The PCM is forwarded to AudioPlaybackStub, which uses AudioTrack
to play it on the device.

4. MediaRouterInboundStub & AudioPlaybackStub
MediaRouterInboundStub
kotlin
Code kopieren
object MediaRouterInboundStub {

    private const val TAG = "MEDIA_ROUTER_INBOUND"

    @JvmStatic
    fun handleDecodedPcm(pcm: ByteArray) {
        Log.d(TAG, "handleDecodedPcm(): got ${'$'}{pcm.size} bytes of PCM, forwarding to AudioPlaybackStub")
        AudioPlaybackStub.enqueuePcm(pcm)
    }
}
Responsibilities:

Single ingress point for decoded PCM.

Logging of frame sizes.

Delegation to AudioPlaybackStub.

AudioPlaybackStub (AudioTrack-based)
kotlin
Code kopieren
object AudioPlaybackStub {

    private const val TAG = "AUDIO_PLAYBACK_STUB"

    private const val SAMPLE_RATE = 48000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val queue = ArrayBlockingQueue<ByteArray>(32)

    @Volatile
    private var worker: Thread? = null

    @JvmStatic
    fun enqueuePcm(pcm: ByteArray) {
        if (!queue.offer(pcm)) {
            Log.w(TAG, "queue full, dropping PCM frame of size=${'$'}{pcm.size}")
            return
        }
        ensureWorker()
    }

    @Synchronized
    private fun ensureWorker() {
        if (worker != null && worker!!.isAlive) return

        val t = Thread {
            Log.d(TAG, "Audio worker started")
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            val audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBuf,
                AudioTrack.MODE_STREAM
            )

            try {
                audioTrack.play()
                Log.d(TAG, "AudioTrack started (sr=$SAMPLE_RATE, buf=$minBuf)")

                while (!Thread.currentThread().isInterrupted) {
                    val pcm = queue.poll()
                    if (pcm != null) {
                        val written = audioTrack.write(pcm, 0, pcm.size)
                        Log.d(TAG, "wrote $written bytes to AudioTrack (pcm=${'$'}{pcm.size})")
                    } else {
                        Thread.sleep(5)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Playback loop error", t)
            } finally {
                try { audioTrack.stop() } catch (_: Throwable) {}
                try { audioTrack.release() } catch (_: Throwable) {}
                Log.d(TAG, "AudioTrack released")
            }
        }
        t.start()
        worker = t
    }
}
This gives us:

A small PCM queue,

A dedicated playback worker thread,

Logging for start / write / release events.

5. How to Run the Beep Test on MainDev Device
From the repo root:

bash
Code kopieren
cd ~/Desktop/stealth
./tools/android_beep_test.sh
Script behaviour:

Builds the debug APK:

bash
Code kopieren
gradle :client_android:app:assembleDebug
Installs it on the connected device:

bash
Code kopieren
adb install -r client_android/app/build/outputs/apk/debug/app-debug.apk
Launches the app via monkey:

bash
Code kopieren
adb shell monkey -p com.securecall.app -c android.intent.category.LAUNCHER 1
Starts filtered logcat:

bash
Code kopieren
adb logcat | egrep "MEDIA_ROUTER_INBOUND|AUDIO_PLAYBACK_STUB"
On the phone:

Tap Settings → you hear a short 440 Hz beep.

Logcat shows something like:

text
Code kopieren
MEDIA_ROUTER_INBOUND: handleDecodedPcm(): got 24000 bytes of PCM, forwarding to AudioPlaybackStub
AUDIO_PLAYBACK_STUB: Audio worker started
AUDIO_PLAYBACK_STUB: AudioTrack started (sr=48000, buf=15392)
AUDIO_PLAYBACK_STUB: wrote 24000 bytes to AudioTrack (pcm=24000)
6. How Other Devs Can Plug In Their Decoder
If you have a decoder that produces raw PCM:

Make sure the output matches:

16-bit PCM

mono

little endian

48 kHz

Call:

java
Code kopieren
com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(decodedPcm);
Ask the main dev to:

run ./tools/android_beep_test.sh,

exercise the path (e.g. via your trigger),

confirm audio + logs.

You do not need to touch UI, AudioTrack details, or device-specific
configuration. Treat MediaRouterInboundStub.handleDecodedPcm() as the
“black-box loudspeaker input” for Android.
DOC

echo "[OK] Wrote docs/tech/ANDROID_INBOUND_AUDIO_DEBUG.md"
echo "== patch_039 done =="
