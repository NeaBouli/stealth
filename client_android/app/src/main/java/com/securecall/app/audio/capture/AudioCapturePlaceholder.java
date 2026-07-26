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
    private volatile long captureGeneration = 0;
    private Thread thread = null;
    private AudioRecord audioRecord = null;

    public synchronized void start() {
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

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            Log.e(TAG, "start(): AudioRecord.startRecording() failed", e);
            audioRecord.release();
            audioRecord = null;
            OpusEncoder.INSTANCE.release();
            return;
        }
        running = true;
        startCaptureThread(audioRecord, "started");

        Log.d(TAG, "Audio capture STARTED (Opus encoding enabled)");
    }

    private void startCaptureThread(AudioRecord recorder, String state) {
        long generation = ++captureGeneration;
        thread = new Thread(() -> {
            Log.d(TAG, "Capture thread started (sr=" + SAMPLE_RATE + ", frame=" + FRAME_SAMPLES + " samples)");
            short[] buffer = new short[FRAME_SAMPLES];

            while (running && captureGeneration == generation) {
                int read = recorder.read(buffer, 0, FRAME_SAMPLES);
                if (!running || captureGeneration != generation) break;
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
        }, "AudioCaptureThread-" + state);
        thread.start();
    }

    /**
     * Lightweight pause — stops recording and capture thread but keeps AudioRecord
     * and OpusEncoder alive for fast resume. Used during cell call interruptions.
     */
    public synchronized void pause() {
        if (!running) return;
        running = false;
        captureGeneration++;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "pause(): AudioRecord.stop() failed", e);
        }

        Thread lingeringThread = stopCaptureThread();
        if (lingeringThread != null) {
            AudioRecord recorder = audioRecord;
            audioRecord = null;
            OpusEncoder.INSTANCE.release();
            releaseRecorderAfterThread(recorder, lingeringThread);
            Log.w(TAG, "pause(): capture thread did not stop promptly; recorder will be recreated");
        } else {
            Log.d(TAG, "Audio capture PAUSED (AudioRecord + OpusEncoder kept alive)");
        }
    }

    /**
     * Resume from pause — restarts recording on the existing AudioRecord.
     * If AudioRecord was released (full stop), falls back to full start().
     */
    public synchronized void resume() {
        if (running) return;
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "resume(): AudioRecord not available — falling back to full start()");
            start();
            return;
        }

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            Log.e(TAG, "resume(): AudioRecord.startRecording() failed", e);
            stop();
            return;
        }
        running = true;
        startCaptureThread(audioRecord, "resumed");

        Log.d(TAG, "Audio capture RESUMED");
    }

    public synchronized void stop() {
        if (!running && audioRecord == null) return;
        running = false;
        captureGeneration++;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "stop(): AudioRecord.stop() failed", e);
        }

        Thread lingeringThread = stopCaptureThread();

        if (audioRecord != null) {
            if (lingeringThread != null) {
                releaseRecorderAfterThread(audioRecord, lingeringThread);
            } else {
                audioRecord.release();
            }
            audioRecord = null;
        }

        // Release Opus encoder
        OpusEncoder.INSTANCE.release();

        Log.d(TAG, "Audio capture STOPPED");
    }

    private Thread stopCaptureThread() {
        Thread captureThread = thread;
        thread = null;
        if (captureThread == null) return null;
        captureThread.interrupt();
        if (captureThread == Thread.currentThread()) return captureThread;
        try {
            captureThread.join(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return captureThread.isAlive() ? captureThread : null;
    }

    private void releaseRecorderAfterThread(AudioRecord recorder, Thread captureThread) {
        if (recorder == null) return;
        Thread cleanupThread = new Thread(() -> {
            try {
                captureThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                recorder.release();
            }
        }, "AudioCaptureCleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
}
