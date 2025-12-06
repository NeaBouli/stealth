package com.securecall.app.ghostnet.media;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * AUDIO_PLAYBACK_STUB
 *
 * Simple AudioTrack-based playback worker:
 *  - enqueuePcm(byte[]) pushes 16-bit, mono, 48 kHz PCM into a queue
 *  - background thread drains queue and writes to AudioTrack
 */
public class AudioPlaybackStub {

    private static final String TAG = "AUDIO_PLAYBACK_STUB";

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private static volatile Thread worker = null;

    public static void enqueuePcm(byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        queue.offer(pcm);
        ensureWorker();
    }

    private static synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        Thread t = new Thread(AudioPlaybackStub::loop, "AudioPlaybackStubWorker");
        t.setDaemon(true);
        t.start();
        worker = t;
        Log.d(TAG, "Audio worker started");
    }

    private static void loop() {
        AudioTrack audioTrack = null;
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
            );
            if (minBuf <= 0) {
                // Fallback: ~100ms Buffer
                minBuf = SAMPLE_RATE * 2 * 2 / 10;
            }

            audioTrack = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AUDIO_FORMAT)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build(),
                    minBuf,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            audioTrack.play();
            Log.d(TAG, "AudioTrack started (sr=" + SAMPLE_RATE + ", buf=" + minBuf + ")");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] pcm = queue.poll();
                if (pcm != null) {
                    int written = audioTrack.write(pcm, 0, pcm.length);
                    Log.d(TAG, "wrote " + written + " bytes to AudioTrack (pcm=" + pcm.length + ")");
                } else {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Playback loop error", t);
        } finally {
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                } catch (Throwable ignored) {}
                try {
                    audioTrack.release();
                } catch (Throwable ignored) {}
            }
            Log.d(TAG, "AudioTrack released");
        }
    }
}
