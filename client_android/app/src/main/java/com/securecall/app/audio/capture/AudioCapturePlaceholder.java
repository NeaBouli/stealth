package com.securecall.app.audio.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient;

/**
 * Real AudioRecord-based microphone capture.
 * 48 kHz, mono, 16-bit PCM.
 * Sends raw PCM chunks over WebSocket as binary frames.
 */
public class AudioCapturePlaceholder {

    private static final String TAG = "AUDIO_CAPTURE";

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 10ms at 48kHz mono 16-bit = 960 bytes
    private static final int BUFFER_SAMPLES = 480;
    private static final int BUFFER_BYTES = BUFFER_SAMPLES * 2;

    private volatile boolean running = false;
    private Thread thread = null;
    private AudioRecord audioRecord = null;

    public void start() {
        if (running) {
            Log.w(TAG, "start(): already running");
            return;
        }

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBuf <= 0) {
            Log.e(TAG, "start(): getMinBufferSize returned " + minBuf + " — cannot record");
            return;
        }

        int bufSize = Math.max(minBuf, BUFFER_BYTES * 4);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufSize
            );
        } catch (SecurityException e) {
            Log.e(TAG, "start(): RECORD_AUDIO permission not granted", e);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "start(): AudioRecord not initialized");
            audioRecord.release();
            audioRecord = null;
            return;
        }

        running = true;
        audioRecord.startRecording();

        thread = new Thread(() -> {
            Log.d(TAG, "Capture thread started (sr=" + SAMPLE_RATE + ", buf=" + bufSize + ")");
            byte[] buffer = new byte[BUFFER_BYTES];

            while (running) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] chunk;
                    if (read == buffer.length) {
                        chunk = buffer.clone();
                    } else {
                        chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                    }
                    GhostNetWebSocketClient.getInstance().sendBinary(chunk);
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read() returned " + read);
                    break;
                }
            }

            Log.d(TAG, "Capture thread stopped");
        }, "AudioCaptureThread");
        thread.start();

        Log.d(TAG, "Audio capture STARTED");
    }

    public void stop() {
        if (!running) return;
        running = false;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "stop(): AudioRecord.stop() failed", e);
        }

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        Log.d(TAG, "Audio capture STOPPED");
    }
}
