package com.sam.assistant.voice.recognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import java.util.Locale

class OfflineSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onRmsChanged: ((Float) -> Unit)? = null
) {

    companion object {
        private const val TAG = "OfflineSpeechRecognizer"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun startListening() {
        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Cannot start speech recognition while device is locked.")
            onError("Device is locked.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition service not available on device.")
            onError("Speech recognition unavailable.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Speech recognizer ready")
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "User started speaking")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    onRmsChanged?.invoke(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "User stopped speaking")
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error (offline model required)"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout, no input detected"
                        else -> "Recognition error: $error"
                    }
                    Log.w(TAG, errorMsg)
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Speech recognized: \"$text\"")
                    if (text.isNotBlank()) {
                        onResult(text)
                    } else {
                        onError("No words detected.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US", "hi"))
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting speech recognition", e)
            onError(e.message ?: "Recognition failed to start.")
        }
    }

    fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignore
            }
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
        isListening = false
    }

    fun isCurrentlyListening(): Boolean = isListening
}

