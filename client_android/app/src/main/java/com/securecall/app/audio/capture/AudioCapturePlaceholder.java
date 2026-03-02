package com.securecall.app.audio.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.securecall.app.ghostnet.media.codec.OpusEncoder;
import com.securecall.app.net.WebSocketService;

/**
 * Real AudioRecord-based microphone capture with Opus encoding.
 * 48 kHz, mono, 16-bit PCM → Opus encoded frames.
 */
public class AudioCapturePlaceholder {

    private static final String TAG = "AUDIO_CAPTURE";

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 20ms at 48kHz mono = 960 samples (Opus frame size)
    private static final int FRAME_SAMPLES = 960;

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

        int bufSize = Math.max(minBuf, FRAME_SAMPLES * 2 * 4);

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

        // Initialize Opus encoder
        OpusEncoder.INSTANCE.init(SAMPLE_RATE, 1);

        running = true;
        audioRecord.startRecording();

        thread = new Thread(() -> {
            Log.d(TAG, "Capture thread started (sr=" + SAMPLE_RATE + ", frame=" + FRAME_SAMPLES + " samples)");
            short[] buffer = new short[FRAME_SAMPLES];

            while (running) {
                int read = audioRecord.read(buffer, 0, FRAME_SAMPLES);
                if (read == FRAME_SAMPLES) {
                    byte[] encoded = OpusEncoder.INSTANCE.encode(buffer);
                    if (encoded.length > 0) {
                        WebSocketService ws = WebSocketService.Companion.getInstance();
                        if (ws != null) ws.sendBinary(encoded);
                    }
                } else if (read > 0) {
                    // Partial frame — pad with silence and encode
                    short[] padded = new short[FRAME_SAMPLES];
                    System.arraycopy(buffer, 0, padded, 0, read);
                    byte[] encoded = OpusEncoder.INSTANCE.encode(padded);
                    if (encoded.length > 0) {
                        WebSocketService ws = WebSocketService.Companion.getInstance();
                        if (ws != null) ws.sendBinary(encoded);
                    }
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read() returned " + read);
                    break;
                }
            }

            Log.d(TAG, "Capture thread stopped");
        }, "AudioCaptureThread");
        thread.start();

        Log.d(TAG, "Audio capture STARTED (Opus encoding enabled)");
    }

    /**
     * Lightweight pause — stops recording and capture thread but keeps AudioRecord
     * and OpusEncoder alive for fast resume. Used during cell call interruptions.
     */
    public void pause() {
        if (!running) return;
        running = false;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "pause(): AudioRecord.stop() failed", e);
        }

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        Log.d(TAG, "Audio capture PAUSED (AudioRecord + OpusEncoder kept alive)");
    }

    /**
     * Resume from pause — restarts recording on the existing AudioRecord.
     * If AudioRecord was released (full stop), falls back to full start().
     */
    public void resume() {
        if (running) return;
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "resume(): AudioRecord not available — falling back to full start()");
            start();
            return;
        }

        running = true;
        audioRecord.startRecording();

        thread = new Thread(() -> {
            Log.d(TAG, "Capture thread RESUMED (sr=" + SAMPLE_RATE + ", frame=" + FRAME_SAMPLES + " samples)");
            short[] buffer = new short[FRAME_SAMPLES];

            while (running) {
                int read = audioRecord.read(buffer, 0, FRAME_SAMPLES);
                if (read == FRAME_SAMPLES) {
                    byte[] encoded = OpusEncoder.INSTANCE.encode(buffer);
                    if (encoded.length > 0) {
                        WebSocketService ws = WebSocketService.Companion.getInstance();
                        if (ws != null) ws.sendBinary(encoded);
                    }
                } else if (read > 0) {
                    short[] padded = new short[FRAME_SAMPLES];
                    System.arraycopy(buffer, 0, padded, 0, read);
                    byte[] encoded = OpusEncoder.INSTANCE.encode(padded);
                    if (encoded.length > 0) {
                        WebSocketService ws = WebSocketService.Companion.getInstance();
                        if (ws != null) ws.sendBinary(encoded);
                    }
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read() returned " + read);
                    break;
                }
            }

            Log.d(TAG, "Capture thread stopped");
        }, "AudioCaptureThread");
        thread.start();

        Log.d(TAG, "Audio capture RESUMED");
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

        // Release Opus encoder
        OpusEncoder.INSTANCE.release();

        Log.d(TAG, "Audio capture STOPPED");
    }
}
