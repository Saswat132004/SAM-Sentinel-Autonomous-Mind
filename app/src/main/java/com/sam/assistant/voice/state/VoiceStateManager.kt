package com.sam.assistant.voice.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VoiceStateManager {
    private const val TAG = "VoiceStateManager"

    private val _currentState = MutableStateFlow(VoiceState.INACTIVE)
    val currentState: StateFlow<VoiceState> = _currentState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val stateListeners = mutableListOf<(oldState: VoiceState, newState: VoiceState) -> Unit>()

    fun registerListener(listener: (VoiceState, VoiceState) -> Unit) {
        synchronized(stateListeners) {
            stateListeners.add(listener)
        }
    }

    fun unregisterListener(listener: (VoiceState, VoiceState) -> Unit) {
        synchronized(stateListeners) {
            stateListeners.remove(listener)
        }
    }

    /**
     * Safely transitions SAM into a new VoiceState with boundary checks.
     * Returns true if transition was permitted and executed, false otherwise.
     */
    @Synchronized
    fun transitionTo(newState: VoiceState, reason: String = ""): Boolean {
        val oldState = _currentState.value
        if (oldState == newState) return true

        if (!isValidTransition(oldState, newState)) {
            Log.w(TAG, "Illegal state transition attempted: $oldState -> $newState ($reason)")
            return false
        }

        Log.d(TAG, "State transition: $oldState -> $newState | Reason: $reason")
        _currentState.value = newState

        if (newState == VoiceState.ERROR && reason.isNotBlank()) {
            _lastError.value = reason
        } else if (newState == VoiceState.STANDBY) {
            _lastError.value = null
        }

        notifyListeners(oldState, newState)
        return true
    }

    private fun isValidTransition(from: VoiceState, to: VoiceState): Boolean {
        // Any state can transition to ERROR or INACTIVE (e.g. on lock or cancel)
        if (to == VoiceState.ERROR || to == VoiceState.INACTIVE) return true

        return when (from) {
            VoiceState.INACTIVE -> to == VoiceState.STANDBY || to == VoiceState.LISTENING
            VoiceState.STANDBY -> to == VoiceState.WAKE_WORD_DETECTED || to == VoiceState.LISTENING
            VoiceState.WAKE_WORD_DETECTED -> to == VoiceState.SPEAKING || to == VoiceState.LISTENING || to == VoiceState.STANDBY
            VoiceState.LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.STANDBY
            VoiceState.PROCESSING -> to == VoiceState.SPEAKING || to == VoiceState.NEEDS_CLARIFICATION ||
                    to == VoiceState.WAITING_FOR_CONFIRMATION || to == VoiceState.COMPLETED || to == VoiceState.STANDBY
            VoiceState.NEEDS_CLARIFICATION -> to == VoiceState.SPEAKING || to == VoiceState.LISTENING || to == VoiceState.STANDBY
            VoiceState.WAITING_FOR_CONFIRMATION -> to == VoiceState.SPEAKING || to == VoiceState.LISTENING || to == VoiceState.STANDBY
            VoiceState.SPEAKING -> to == VoiceState.FOLLOW_UP_LISTENING || to == VoiceState.LISTENING ||
                    to == VoiceState.STANDBY || to == VoiceState.COMPLETED
            VoiceState.FOLLOW_UP_LISTENING -> to == VoiceState.PROCESSING || to == VoiceState.LISTENING || to == VoiceState.STANDBY
            VoiceState.COMPLETED -> to == VoiceState.FOLLOW_UP_LISTENING || to == VoiceState.STANDBY || to == VoiceState.LISTENING
            VoiceState.ERROR -> to == VoiceState.STANDBY || to == VoiceState.INACTIVE
        }
    }

    fun setRecognizedText(text: String) {
        _lastRecognizedText.value = text
    }

    fun setSpokenText(text: String) {
        _lastSpokenText.value = text
    }

    fun setError(error: String) {
        _lastError.value = error
        transitionTo(VoiceState.ERROR, error)
    }

    fun updateRms(rmsdB: Float) {
        _audioRmsDb.value = rmsdB
    }

    fun reset() {
        _currentState.value = VoiceState.INACTIVE
        _lastRecognizedText.value = ""
        _lastSpokenText.value = ""
        _lastError.value = null
        _audioRmsDb.value = 0f
    }

    private fun notifyListeners(oldState: VoiceState, newState: VoiceState) {
        val listenersCopy = synchronized(stateListeners) { stateListeners.toList() }
        for (listener in listenersCopy) {
            try {
                listener(oldState, newState)
            } catch (e: Exception) {
                Log.e(TAG, "Error in VoiceState listener", e)
            }
        }
    }
}
