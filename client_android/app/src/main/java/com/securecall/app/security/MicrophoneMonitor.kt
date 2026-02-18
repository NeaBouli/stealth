package com.securecall.app.security

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Monitors microphone usage by other apps during active calls.
 *
 * Uses AudioManager.getActiveRecordingConfigurations() on Android 10+ (API 29)
 * to detect if another application is simultaneously recording audio.
 */
class MicrophoneMonitor(context: Context) {

    private val TAG = "MicrophoneMonitor"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var isMonitoring = false

    /** Callback when another app starts/stops recording. */
    var onOtherAppRecording: ((Boolean, List<String>) -> Unit)? = null

    /**
     * Start monitoring microphone usage by other apps.
     * Registers a callback for real-time recording configuration changes.
     */
    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recordingCallback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    checkRecordingConfigurations(configs)
                }
            }
            audioManager.registerAudioRecordingCallback(recordingCallback!!, handler)
            isMonitoring = true
            Log.d(TAG, "Microphone monitoring started")

            // Initial check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkRecordingConfigurations(audioManager.activeRecordingConfigurations)
            }
        } else {
            Log.w(TAG, "Microphone monitoring not available below API 24")
        }
    }

    /**
     * Analyze recording configurations to detect other apps recording.
     */
    private fun checkRecordingConfigurations(configs: List<AudioRecordingConfiguration>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        // Our app's package name
        val myUid = android.os.Process.myUid()
        val otherRecorders = mutableListOf<String>()

        for (config in configs) {
            try {
                val clientUid = config.clientAudioSessionId
                // On API 29+, we can check the client effects
                // Any recording configuration from a different UID is suspicious
                val audioSource = config.clientAudioSource
                if (audioSource == android.media.MediaRecorder.AudioSource.MIC ||
                    audioSource == android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION ||
                    audioSource == android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION ||
                    audioSource == android.media.MediaRecorder.AudioSource.CAMCORDER
                ) {
                    // Check if this is from our own app
                    if (clientUid != android.media.AudioRecord.getMinBufferSize(
                            44100, android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        )
                    ) {
                        otherRecorders.add("UID:$clientUid source:$audioSource")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking recording config", e)
            }
        }

        // If more than 1 active recording (our own + another), flag it
        if (configs.size > 1) {
            Log.w(TAG, "Multiple active recordings detected: ${configs.size}")
            onOtherAppRecording?.invoke(true, otherRecorders)
        } else {
            onOtherAppRecording?.invoke(false, emptyList())
        }
    }

    /**
     * Get count of currently active recording sessions.
     */
    fun getActiveRecordingCount(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager.activeRecordingConfigurations.size
        } else {
            0
        }
    }

    /**
     * Check if another app is currently recording.
     */
    fun isOtherAppRecording(): Boolean {
        return getActiveRecordingCount() > 1
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recordingCallback != null) {
            audioManager.unregisterAudioRecordingCallback(recordingCallback!!)
            recordingCallback = null
            isMonitoring = false
            Log.d(TAG, "Microphone monitoring stopped")
        }
    }
}
