package com.sam.assistant.voice.recognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.voice.audio.AudioSessionManager
import com.sam.assistant.voice.audio.AudioSessionOwner
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import com.sam.assistant.voice.vad.VadConfig
import com.sam.assistant.voice.vad.VoiceActivityDetector
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val audioSessionManager: AudioSessionManager,
    private val onResult: (RecognitionResult) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
        private const val VOSK_MODEL_DIR_NAME = "vosk-model"
        private const val SAMPLE_RATE = 16000
    }

    private var recognitionJob: Job? = null
    private var isRecognizing = false
    private var sessionStartTimeMs = 0L

    // Vosk Components
    private var voskModel: Model? = null
    private var isVoskLoaded = false

    // Fallback Android Offline Recognizer
    private var fallbackRecognizer: SpeechRecognizer? = null

    // Integrated Voice Activity Detector
    private val vad = VoiceActivityDetector(
        config = VadConfig(endOfSpeechSilenceMs = 2000L),
        onSpeechStart = {
            Log.d(TAG, "VAD: User began speaking command")
            VoiceStateManager.transitionTo(VoiceState.LISTENING, "Speech detected")
        },
        onSpeechOngoing = { rms ->
            VoiceStateManager.updateRms(rms)
        },
        onSpeechEnd = { durationMs ->
            Log.d(TAG, "VAD: Silence detected. Auto-finalizing utterance (Duration: $durationMs ms)")
            finishRecognitionSession()
        },
        onRmsChanged = { rms ->
            VoiceStateManager.updateRms(rms)
        }
    )

    init {
        initVoskModelAsync()
    }

    /**
     * Initializes the Vosk Kaldi speech recognition model if present in internal storage.
     */
    private fun initVoskModelAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelDir = File(context.filesDir, VOSK_MODEL_DIR_NAME)
                if (modelDir.exists() && modelDir.isDirectory) {
                    val files = modelDir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        Log.d(TAG, "Loading Vosk speech model from ${modelDir.absolutePath}...")
                        voskModel = Model(modelDir.absolutePath)
                        isVoskLoaded = true
                        Log.d(TAG, "Vosk model successfully loaded into memory!")
                    }
                } else {
                    Log.d(TAG, "Vosk model directory not yet populated ($VOSK_MODEL_DIR_NAME). Offline Android Recognizer active as primary fallback.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vosk model loading exception: ${e.message}. Using offline fallback recognizer.")
                isVoskLoaded = false
            }
        }
    }

    /**
     * Starts listening for user command.
     */
    fun startListening(scope: CoroutineScope) {
        if (isRecognizing) return

        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Speech recognition blocked: Device is locked.")
            onError("Device is locked.")
            return
        }

        stopListening()
        vad.reset()
        sessionStartTimeMs = System.currentTimeMillis()

        if (isVoskLoaded && voskModel != null) {
            startVoskRecognitionStream(scope)
        } else {
            startOfflineFallbackRecognition()
        }
    }

    /**
     * Primary Offline ASR: Vosk Streaming Speech Recognizer.
     */
    private fun startVoskRecognitionStream(scope: CoroutineScope) {
        val record = audioSessionManager.acquireMicrophone(
            AudioSessionOwner.SPEECH_RECOGNITION,
            SAMPLE_RATE
        )

        if (record == null) {
            Log.e(TAG, "Failed to acquire microphone for Vosk recognition.")
            onError("Microphone unavailable")
            VoiceStateManager.setError("Microphone unavailable")
            return
        }

        try {
            val recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat())
            recognizer.setMaxAlternatives(1)
            recognizer.setWords(true)

            record.startRecording()
            isRecognizing = true
            VoiceStateManager.transitionTo(VoiceState.LISTENING, "Vosk offline ASR started")

            recognitionJob = scope.launch(Dispatchers.IO) {
                val bufferSize = 2048
                val audioBuffer = ShortArray(bufferSize)

                while (isActive && isRecognizing) {
                    if (LockStateManager.isLocked.value) {
                        Log.w(TAG, "Lock engaged during speech recognition -> aborting")
                        break
                    }

                    val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
                    if (readShorts > 0) {
                        // 1. Process VAD on raw audio chunk
                        vad.processFrame(audioBuffer, readShorts)

                        // 2. Feed chunk to Vosk Recognizer
                        if (recognizer.acceptWaveForm(audioBuffer, readShorts)) {
                            // Vosk detected phrase boundary
                            val resultJsonStr = recognizer.result
                            val parsedText = extractVoskText(resultJsonStr)
                            if (parsedText.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    dispatchFinalResult(parsedText, "Vosk Offline ASR")
                                }
                                break
                            }
                        }
                    }
                }

                // Finalize any trailing hypothesis
                if (isRecognizing) {
                    val finalResultJson = recognizer.finalResult
                    val finalText = extractVoskText(finalResultJson)
                    withContext(Dispatchers.Main) {
                        dispatchFinalResult(finalText, "Vosk Offline ASR")
                    }
                }

                recognizer.close()
                stopListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Vosk streaming recognizer", e)
            stopListening()
            startOfflineFallbackRecognition()
        }
    }

    /**
     * Fallback Offline ASR: Native Android Offline SpeechRecognizer.
     */
    private fun startOfflineFallbackRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on device.")
            onError("Speech recognition unavailable.")
            VoiceStateManager.setError("Speech recognition unavailable")
            return
        }

        isRecognizing = true
        VoiceStateManager.transitionTo(VoiceState.LISTENING, "Offline fallback ASR started")

        fallbackRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Fallback recognizer ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Fallback recognizer: Speech began")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    VoiceStateManager.updateRms(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Fallback recognizer: End of speech detected")
                    VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Speech ended")
                }

                override fun onError(error: Int) {
                    isRecognizing = false
                    val errorMsg = mapRecognizerError(error)
                    Log.w(TAG, "Fallback recognition error: $errorMsg ($error)")
                    onError(errorMsg)
                    VoiceStateManager.setError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isRecognizing = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    dispatchFinalResult(text, "Android Offline ASR")
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
            fallbackRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking fallback SpeechRecognizer", e)
            isRecognizing = false
            onError(e.message ?: "Recognition failed")
            VoiceStateManager.setError("Recognition failed to start")
        }
    }

    private fun finishRecognitionSession() {
        if (!isRecognizing) return
        isRecognizing = false
        VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Auto-finalized by VAD")
    }

    private fun dispatchFinalResult(text: String, engine: String) {
        val duration = System.currentTimeMillis() - sessionStartTimeMs
        if (text.isNotBlank()) {
            VoiceStateManager.setRecognizedText(text)
            VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Command transcribed: \"$text\"")
            onResult(
                RecognitionResult(
                    text = text,
                    isFinal = true,
                    confidence = 0.95f,
                    rawHypothesis = text,
                    durationMs = duration,
                    engineUsed = engine
                )
            )
        } else {
            onError("No command detected.")
            VoiceStateManager.transitionTo(VoiceState.STANDBY, "Empty utterance")
        }
    }

    private fun extractVoskText(jsonStr: String): String {
        return try {
            val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
            jsonObject.get("text")?.asString?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun mapRecognizerError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
            SpeechRecognizer.ERROR_NETWORK -> "Network error (offline speech model needed)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (no speech detected)"
            else -> "Recognition error $error"
        }
    }

    fun stopListening() {
        isRecognizing = false
        recognitionJob?.cancel()
        recognitionJob = null
        audioSessionManager.releaseMicrophone(AudioSessionOwner.SPEECH_RECOGNITION)

        try {
            fallbackRecognizer?.stopListening()
            fallbackRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        fallbackRecognizer = null
        Log.d(TAG, "Speech recognition stopped and microphone released.")
    }

    fun isRecognizing(): Boolean = isRecognizing
    fun isVoskModelReady(): Boolean = isVoskLoaded
    fun getVoskModelDirectory(): File = File(context.filesDir, VOSK_MODEL_DIR_NAME)
}
