package com.sam.assistant.voice.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import java.util.Locale

class TextToSpeechEngine(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) {

    companion object {
        private const val TAG = "TextToSpeechEngine"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.05f)
                tts?.setSpeechRate(1.0f)
                isInitialized = true
                Log.d(TAG, "TextToSpeech initialized successfully")
                onInitComplete?.invoke(true)
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech")
                onInitComplete?.invoke(false)
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready yet: $text")
            onDone?.invoke()
            return
        }

        // Lock-state security: if locked, do not speak sensitive personal data
        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Device is locked. Suppressing voice readout for privacy.")
            onDone?.invoke()
            return
        }

        val utteranceId = "SAM_TTS_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d(TAG, "TTS started speaking: $id")
            }

            override fun onDone(id: String?) {
                Log.d(TAG, "TTS finished speaking: $id")
                onDone?.invoke()
            }

            override fun onError(id: String?) {
                Log.e(TAG, "TTS error speaking: $id")
                onDone?.invoke()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun setSpeedAndPitch(rate: Float, pitch: Float) {
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }
}

