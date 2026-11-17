package com.sam.assistant.voice.vad

import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class VadConfig(
    val initialSpeechWaitMs: Long = 15000L,     // Wait up to 15s for user to begin speaking
    val endOfSpeechSilenceMs: Long = 2000L,     // 2s silence to finalize utterance
    val pauseAllowanceMs: Long = 5000L,         // Up to 5s pause allowance during continuous thought
    val maxUtteranceDurationMs: Long = 45000L,  // 45s maximum utterance safety ceiling
    val minSpeechDurationMs: Long = 250L,       // Minimum speech to count as valid command
    val energyThresholdOffset: Double = 12.0,   // dB above dynamic noise floor
    val sampleRate: Int = 16000
)

class VoiceActivityDetector(
    private val config: VadConfig = VadConfig(),
    private val onSpeechStart: () -> Unit = {},
    private val onSpeechOngoing: (rmsDb: Float) -> Unit = {},
    private val onSpeechEnd: (totalDurationMs: Long) -> Unit = {},
    private val onInitialWaitTimeout: () -> Unit = {},
    private val onRmsChanged: (rmsDb: Float) -> Unit = {}
) {

    companion object {
        private const val TAG = "VoiceActivityDetector"
    }

    private var isSpeechActive = false
    private var sessionStartTimeMs = 0L
    private var speechStartTimeMs = 0L
    private var lastSpeechTimeMs = 0L
    private var silenceStartTimeMs = 0L

    // Adaptive noise floor tracking (in dB)
    private var noiseFloorDb = 35.0
    private var noiseSampleCount = 0

    fun reset() {
        isSpeechActive = false
        sessionStartTimeMs = System.currentTimeMillis()
        speechStartTimeMs = 0L
        lastSpeechTimeMs = 0L
        silenceStartTimeMs = 0L
        noiseFloorDb = 35.0
        noiseSampleCount = 0
    }

    /**
     * Processes a chunk of 16-bit PCM audio samples.
     */
    fun processFrame(buffer: ShortArray, readShorts: Int) {
        if (readShorts <= 0) return

        val now = System.currentTimeMillis()

        // Initialize session start time if not set
        if (sessionStartTimeMs == 0L) {
            sessionStartTimeMs = now
        }

        // 1. Calculate RMS energy
        var sumSquares = 0.0
        for (i in 0 until readShorts) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }

        val rms = sqrt(sumSquares / readShorts)
        val rmsDb = if (rms > 0.0) 20.0 * log10(rms) else 0.0
        val clampedRmsDb = max(0.0, rmsDb).toFloat()

        onRmsChanged(clampedRmsDb)

        // 2. Adaptive noise floor calibration
        val speechThresholdDb = noiseFloorDb + config.energyThresholdOffset
        val isCurrentFrameSpeech = clampedRmsDb > speechThresholdDb

        if (!isSpeechActive && !isCurrentFrameSpeech) {
            if (noiseSampleCount < 80) {
                noiseFloorDb = (noiseFloorDb * noiseSampleCount + clampedRmsDb) / (noiseSampleCount + 1)
                noiseSampleCount++
            } else {
                noiseFloorDb = 0.98 * noiseFloorDb + 0.02 * clampedRmsDb
            }

            // Check initial speech timeout (15s wait for first word)
            if (now - sessionStartTimeMs > config.initialSpeechWaitMs) {
                Log.d(TAG, "Initial speech wait timed out after ${config.initialSpeechWaitMs} ms.")
                onInitialWaitTimeout()
                return
            }
        }

        // 3. VAD State Tracking
        if (isCurrentFrameSpeech) {
            lastSpeechTimeMs = now
            silenceStartTimeMs = 0L

            if (!isSpeechActive) {
                isSpeechActive = true
                speechStartTimeMs = now
                Log.d(TAG, "Speech start detected (RMS: ${clampedRmsDb.toInt()} dB, Noise floor: ${noiseFloorDb.toInt()} dB)")
                onSpeechStart()
            } else {
                onSpeechOngoing(clampedRmsDb)
            }

            // Safety limit: prevent runaway recordings beyond maxUtteranceDurationMs
            val totalSpeechDuration = now - speechStartTimeMs
            if (totalSpeechDuration > config.maxUtteranceDurationMs) {
                Log.w(TAG, "Maximum utterance duration exceeded (${config.maxUtteranceDurationMs} ms) -> auto-finalizing")
                isSpeechActive = false
                onSpeechEnd(totalSpeechDuration)
            }
        } else {
            // Frame is silence
            if (isSpeechActive) {
                if (silenceStartTimeMs == 0L) {
                    silenceStartTimeMs = now
                }

                val silenceDuration = now - silenceStartTimeMs
                val totalSpeechDuration = now - speechStartTimeMs

                // Determine required silence: 2000ms standard end-of-speech
                if (silenceDuration >= config.endOfSpeechSilenceMs) {
                    if (totalSpeechDuration >= config.minSpeechDurationMs) {
                        Log.d(TAG, "Speech end detected. Silence: $silenceDuration ms, Total utterance: $totalSpeechDuration ms")
                        isSpeechActive = false
                        onSpeechEnd(totalSpeechDuration)
                    } else {
                        // Short acoustic spike, ignore
                        isSpeechActive = false
                    }
                }
            }
        }
    }

    fun isSpeechActive(): Boolean = isSpeechActive
}
