package com.sam.assistant.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.voice.language.LanguageDetectionManager
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import java.util.Locale

class TextToSpeechManager(
    private val context: Context,
    private val onInitResult: ((Boolean) -> Unit)? = null
) {

    companion object {
        private const val TAG = "TextToSpeechManager"
        const val ACKNOWLEDGEMENT_PHRASE = "Yes, I'm listening."
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isCurrentlySpeaking = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private var speechRate = 1.0f
    private var speechPitch = 1.05f

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialize default voice to Indian English / device default
                val defaultLocale = Locale("en", "IN")
                val result = tts?.setLanguage(defaultLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setPitch(speechPitch)
                tts?.setSpeechRate(speechRate)
                isInitialized = true
                Log.d(TAG, "TextToSpeechManager initialized successfully.")
                onInitResult?.invoke(true)
            } else {
                Log.e(TAG, "Failed to initialize Android TextToSpeech engine. Status: $status")
                isInitialized = false
                VoiceStateManager.setError("TTS engine failed to initialize")
                onInitResult?.invoke(false)
            }
        }
    }

    /**
     * Speaks the wake-phrase acknowledgement: "Yes, I'm listening."
     */
    fun speakAcknowledgement(onDone: (() -> Unit)? = null) {
        speak(ACKNOWLEDGEMENT_PHRASE, onDone = onDone)
    }

    /**
     * Speaks text with automatic language voice selection (Hindi vs English),
     * audio focus management, and speech completion callbacks.
     */
    @Synchronized
    fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready: \"$text\"")
            onDone?.invoke()
            return
        }

        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Device locked -> suppressing spoken TTS output.")
            onDone?.invoke()
            return
        }

        // Stop any active utterance before starting a new one (no overlapping)
        stop()

        // 1. Language & Voice Selection
        val targetLocale = LanguageDetectionManager.getRecommendedTtsLocale(text)
        try {
            val langResult = tts?.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English
                tts?.setLanguage(Locale("en", "IN"))
            }
        } catch (_: Exception) {}

        requestTtsAudioFocus()

        val utteranceId = "SAM_TTS_${System.currentTimeMillis()}_${(100..999).random()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                isCurrentlySpeaking = true
                Log.d(TAG, "TTS playback started ($id): \"$text\"")
                VoiceStateManager.setSpokenText(text)
                VoiceStateManager.transitionTo(VoiceState.SPEAKING, "Speaking TTS output")
                onStart?.invoke()
            }

            override fun onDone(id: String?) {
                isCurrentlySpeaking = false
                abandonTtsAudioFocus()
                Log.d(TAG, "TTS playback completed ($id)")
                onDone?.invoke()
            }

            override fun onError(id: String?) {
                isCurrentlySpeaking = false
                abandonTtsAudioFocus()
                Log.e(TAG, "TTS playback error ($id)")
                onError?.invoke("Playback error")
                onDone?.invoke()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speech interruption: instantly halts speech synthesis if user interrupts or cancels.
     */
    @Synchronized
    fun interrupt() {
        if (isCurrentlySpeaking) {
            Log.d(TAG, "TTS speech interrupted by user.")
            stop()
        }
    }

    /**
     * Halts active speech immediately and abandons audio focus.
     */
    @Synchronized
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping TTS: ${e.message}")
        } finally {
            isCurrentlySpeaking = false
            abandonTtsAudioFocus()
        }
    }

    fun isSpeaking(): Boolean = isCurrentlySpeaking

    fun setSpeechParameters(rate: Float, pitch: Float) {
        this.speechRate = rate.coerceIn(0.5f, 2.0f)
        this.speechPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }

    private fun requestTtsAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .build()

                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonTtsAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }
}
