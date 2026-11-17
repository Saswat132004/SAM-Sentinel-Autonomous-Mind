package com.sam.assistant.voice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.sam.assistant.core.permissions.PermissionManager

class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var activeOwner = AudioSessionOwner.NONE
    private var activeRecord: AudioRecord? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var interruptionListener: ((Boolean) -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost ($focusChange). Halting active capture.")
                interruptionListener?.invoke(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained.")
                interruptionListener?.invoke(false)
            }
        }
    }

    fun setInterruptionListener(listener: ((isInterrupted: Boolean) -> Unit)?) {
        this.interruptionListener = listener
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startCapture(
        owner: AudioSessionOwner,
        sampleRate: Int = SAMPLE_RATE_HZ
    ): AudioRecord? {
        if (!PermissionManager.hasRecordAudioPermission(context)) {
            Log.e(TAG, "Cannot start capture: Missing RECORD_AUDIO permission.")
            return null
        }

        // Release existing session if switching owners
        if (activeOwner != AudioSessionOwner.NONE && activeOwner != owner) {
            Log.d(TAG, "Releasing existing session from $activeOwner for new owner $owner")
            stopCapture(activeOwner)
        }

        requestAudioFocus()

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer size calculation: $minBufferSize")
            abandonAudioFocus()
            return null
        }
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        return try {
            // Priority 1: VOICE_RECOGNITION source (optimized for speech models)
            var record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // Priority 2: Fallback to MIC if device driver fails to initialize VOICE_RECOGNITION
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "VOICE_RECOGNITION uninitialized. Falling back to AudioSource.MIC...")
                record.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize across all audio sources for $owner")
                record.release()
                abandonAudioFocus()
                return null
            }

            activeRecord = record
            activeOwner = owner
            Log.d(TAG, "Microphone capture successfully started by $owner at $sampleRate Hz")
            record
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioRecord for $owner", e)
            abandonAudioFocus()
            null
        }
    }

    @Synchronized
    fun stopCapture(owner: AudioSessionOwner) {
        if (activeOwner == owner || owner == AudioSessionOwner.NONE) {
            Log.d(TAG, "Stopping microphone capture for $owner")
            try {
                if (activeRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    activeRecord?.stop()
                }
                activeRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Exception during AudioRecord release: ${e.message}")
            } finally {
                activeRecord = null
                activeOwner = AudioSessionOwner.NONE
                abandonAudioFocus()
            }
        }
    }

    fun isCapturing(): Boolean = activeRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    fun getActiveOwner(): AudioSessionOwner = activeOwner

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()

                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioFocus request failed: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioFocus abandon failed: ${e.message}")
        }
    }
}
