package com.sam.assistant.voice.wakeword

import android.media.AudioRecord
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.voice.audio.AudioSessionManager
import com.sam.assistant.voice.audio.AudioSessionOwner
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import kotlinx.coroutines.*
import kotlin.math.sqrt

class WakeWordDetector(
    private val audioSessionManager: AudioSessionManager,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val onWakeWordTriggered: () -> Unit
) {

    companion object {
        private const val TAG = "WakeWordDetector"
    }

    private var detectionJob: Job? = null
    private var isRunning = false
    private var lastTriggerTimeMs = 0L

    init {
        // Enforce lock-state security: stop immediately if locked
        LockStateManager.registerListener { isLocked ->
            if (isLocked) {
                Log.d(TAG, "Device locked -> halting wake-word detector and releasing microphone")
                stopListening()
            }
        }
    }

    /**
     * Starts background passive keyword listening for "Hey SAM".
     */
    fun startListening(scope: CoroutineScope) {
        if (isRunning) return

        // Security check
        if (LockStateManager.isLocked.value) {
            Log.d(TAG, "Cannot start wake-word detection: Device is locked.")
            return
        }

        val record = audioSessionManager.acquireMicrophone(
            AudioSessionOwner.WAKE_WORD,
            config.sampleRate
        )

        if (record == null) {
            Log.e(TAG, "Failed to acquire microphone for wake-word detection.")
            VoiceStateManager.setError("Microphone unavailable for wake-word engine")
            return
        }

        try {
            record.startRecording()
            isRunning = true
            VoiceStateManager.transitionTo(VoiceState.STANDBY, "Wake-word engine started")
            Log.d(TAG, "Wake-word detector active, listening for \"${config.wakePhrase}\" (Sensitivity: ${config.sensitivity})")

            detectionJob = scope.launch(Dispatchers.IO) {
                val bufferSize = 2048
                val audioBuffer = ShortArray(bufferSize)

                // Temporal cadence state machine for "Hey" + "SAM"
                var syllable1Frames = 0
                var gapFrames = 0
                var syllable2Frames = 0
                var inGap = false

                val energyThreshold = config.effectiveEnergyThreshold

                while (isActive && isRunning) {
                    if (LockStateManager.isLocked.value) {
                        Log.d(TAG, "Lock detected during listening loop -> exiting wake-word engine")
                        break
                    }

                    val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
                    if (readShorts > 0) {
                        // Calculate RMS energy
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            sum += audioBuffer[i] * audioBuffer[i]
                        }
                        val rms = sqrt(sum / readShorts)

                        // Update audio visualizer in standby if active
                        VoiceStateManager.updateRms(rms.toFloat())

                        val isVoiced = rms > energyThreshold

                        if (isVoiced) {
                            if (!inGap && syllable1Frames < config.maxAcousticBurstFrames) {
                                // Accumulating Syllable 1 ("Hey")
                                syllable1Frames++
                            } else if (inGap && gapFrames <= config.interSyllableGapFrames) {
                                // Syllable 2 started ("SAM")
                                syllable2Frames++
                            }
                        } else {
                            // Silence frame
                            if (syllable1Frames >= config.minAcousticBurstFrames && syllable2Frames == 0) {
                                // Completed Syllable 1, entering inter-syllable gap
                                inGap = true
                                gapFrames++
                                if (gapFrames > config.interSyllableGapFrames) {
                                    // Gap too long, reset
                                    syllable1Frames = 0
                                    syllable2Frames = 0
                                    gapFrames = 0
                                    inGap = false
                                }
                            } else if (syllable2Frames >= config.minAcousticBurstFrames) {
                                // Syllable 2 completed! Full "Hey SAM" cadence matched!
                                val now = System.currentTimeMillis()
                                if (now - lastTriggerTimeMs > config.cooldownPeriodMs) {
                                    lastTriggerTimeMs = now
                                    Log.d(TAG, "Acoustic cadence match for \"${config.wakePhrase}\"! (Syllable1: $syllable1Frames frames, Gap: $gapFrames frames, Syllable2: $syllable2Frames frames)")

                                    // Reset counters
                                    syllable1Frames = 0
                                    syllable2Frames = 0
                                    gapFrames = 0
                                    inGap = false

                                    withContext(Dispatchers.Main) {
                                        triggerActivation()
                                    }
                                    break // Release mic for ASR session
                                }
                            } else {
                                // Idle silence
                                if (gapFrames > 0) {
                                    gapFrames++
                                    if (gapFrames > config.interSyllableGapFrames) {
                                        syllable1Frames = 0
                                        syllable2Frames = 0
                                        gapFrames = 0
                                        inGap = false
                                    }
                                }
                            }
                        }
                    }

                    delay(25) // Low CPU throttle (~2% on Dimensity 7400 Ultra)
                }

                stopListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake-word detection loop", e)
            VoiceStateManager.setError("Wake-word error: ${e.message}")
            stopListening()
        }
    }

    private fun triggerActivation() {
        stopListening()
        VoiceStateManager.transitionTo(VoiceState.WAKE_WORD_DETECTED, "Hey SAM detected")
        onWakeWordTriggered()
    }

    fun stopListening() {
        isRunning = false
        detectionJob?.cancel()
        detectionJob = null
        audioSessionManager.releaseMicrophone(AudioSessionOwner.WAKE_WORD)
        Log.d(TAG, "Wake-word detector stopped.")
    }

    fun isListening(): Boolean = isRunning
}
