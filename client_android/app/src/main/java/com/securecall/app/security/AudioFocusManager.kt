package com.securecall.app.security

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages exclusive audio focus during encrypted calls.
 *
 * Requests AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE to prevent other apps
 * from recording audio while a SecureCall is active.
 */
class AudioFocusManager(context: Context) {

    private val TAG = "AudioFocusManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus LOST — another app took exclusive access")
                hasFocus = false
                onFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost transiently")
                hasFocus = false
                onFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained")
                hasFocus = true
            }
        }
    }

    /** Callback invoked when audio focus is stolen by another app. */
    var onFocusLost: (() -> Unit)? = null

    /**
     * Request exclusive audio focus.
     * Returns true if focus was granted.
     */
    fun requestExclusiveFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

            if (hasFocus) {
                Log.d(TAG, "Exclusive audio focus GRANTED")
            } else {
                Log.w(TAG, "Exclusive audio focus DENIED (result=$result)")
            }
            return hasFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return hasFocus
        }
    }

    /**
     * Release audio focus when call ends.
     */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                Log.d(TAG, "Audio focus released")
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
        focusRequest = null
    }

    fun hasFocus(): Boolean = hasFocus
}
