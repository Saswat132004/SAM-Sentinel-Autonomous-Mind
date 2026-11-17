package com.sam.assistant.voice.wakeword

import android.content.Context
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.voice.audio.AudioCaptureManager
import com.sam.assistant.voice.audio.AudioSessionManager
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import kotlinx.coroutines.CoroutineScope

class WakeWordManager(
    private val context: Context,
    private val audioSessionManager: AudioSessionManager,
    private var config: WakeWordConfig = WakeWordConfig(),
    private val onWakeWordDetected: () -> Unit
) {

    companion object {
        private const val TAG = "WakeWordManager"
    }

    private val detector = WakeWordDetector(
        audioSessionManager = audioSessionManager,
        config = config,
        onWakeWordTriggered = {
            handleWakeWordTrigger()
        }
    )

    init {
        LockStateManager.registerListener { isLocked ->
            if (isLocked) {
                Log.d(TAG, "Device locked -> halting wake word detection")
                stopStandby()
            }
        }
    }

    fun startStandby(scope: CoroutineScope) {
        if (LockStateManager.isLocked.value) {
            Log.d(TAG, "Device locked -> wake-word standby blocked.")
            VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Device is locked")
            return
        }

        Log.d(TAG, "Entering wake-word standby mode for \"${config.wakePhrase}\"")
        detector.startListening(scope)
    }

    fun stopStandby() {
        detector.stopListening()
        Log.d(TAG, "Wake-word standby halted.")
    }

    fun updateSensitivity(sensitivity: Float) {
        config = config.copy(sensitivity = sensitivity.coerceIn(0.1f, 1.0f))
        Log.d(TAG, "Wake-word sensitivity updated to $sensitivity")
    }

    fun isListening(): Boolean = detector.isListening()

    fun getConfig(): WakeWordConfig = config

    private fun handleWakeWordTrigger() {
        Log.d(TAG, "Wake-word \"${config.wakePhrase}\" verified! Triggering activation callback.")
        onWakeWordDetected()
    }
}
