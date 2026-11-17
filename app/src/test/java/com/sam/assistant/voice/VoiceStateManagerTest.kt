package com.sam.assistant.voice

import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceStateManagerTest {

    @Before
    fun setUp() {
        VoiceStateManager.reset()
    }

    @Test
    fun testInitialStateIsInactive() {
        assertEquals(VoiceState.INACTIVE, VoiceStateManager.currentState.value)
    }

    @Test
    fun testValidTransitionPipeline() {
        // INACTIVE -> STANDBY
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY, "Service started"))
        assertEquals(VoiceState.STANDBY, VoiceStateManager.currentState.value)

        // STANDBY -> WAKE_WORD_DETECTED ("Hey SAM")
        assertTrue(VoiceStateManager.transitionTo(VoiceState.WAKE_WORD_DETECTED, "Hey SAM detected"))
        assertEquals(VoiceState.WAKE_WORD_DETECTED, VoiceStateManager.currentState.value)

        // WAKE_WORD_DETECTED -> SPEAKING ("Yes, I'm listening.")
        assertTrue(VoiceStateManager.transitionTo(VoiceState.SPEAKING, "Acknowledging"))
        assertEquals(VoiceState.SPEAKING, VoiceStateManager.currentState.value)

        // SPEAKING -> LISTENING (Command recording)
        assertTrue(VoiceStateManager.transitionTo(VoiceState.LISTENING, "Recording user command"))
        assertEquals(VoiceState.LISTENING, VoiceStateManager.currentState.value)

        // LISTENING -> PROCESSING (VAD detected silence, ASR transcribing)
        assertTrue(VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Transcribing speech"))
        assertEquals(VoiceState.PROCESSING, VoiceStateManager.currentState.value)

        // PROCESSING -> SPEAKING (Delivering response)
        assertTrue(VoiceStateManager.transitionTo(VoiceState.SPEAKING, "Delivering answer"))
        assertEquals(VoiceState.SPEAKING, VoiceStateManager.currentState.value)

        // SPEAKING -> STANDBY (Completed)
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY, "Returned to standby"))
        assertEquals(VoiceState.STANDBY, VoiceStateManager.currentState.value)
    }

    @Test
    fun testIllegalTransitionRejected() {
        // INACTIVE cannot jump directly to PROCESSING
        assertFalse(VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Illegal jump"))
        assertEquals(VoiceState.INACTIVE, VoiceStateManager.currentState.value)
    }

    @Test
    fun testErrorTransitionAndRecovery() {
        VoiceStateManager.transitionTo(VoiceState.STANDBY)
        VoiceStateManager.setError("Microphone permission missing")

        assertEquals(VoiceState.ERROR, VoiceStateManager.currentState.value)
        assertEquals("Microphone permission missing", VoiceStateManager.lastError.value)

        // Recovery to STANDBY
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY, "Recovered"))
        assertEquals(VoiceState.STANDBY, VoiceStateManager.currentState.value)
        assertNull(VoiceStateManager.lastError.value)
    }

    @Test
    fun testClarificationAndFollowUpTransitions() {
        VoiceStateManager.transitionTo(VoiceState.STANDBY)
        VoiceStateManager.transitionTo(VoiceState.LISTENING)
        VoiceStateManager.transitionTo(VoiceState.PROCESSING)

        // PROCESSING -> NEEDS_CLARIFICATION
        assertTrue(VoiceStateManager.transitionTo(VoiceState.NEEDS_CLARIFICATION))
        assertEquals(VoiceState.NEEDS_CLARIFICATION, VoiceStateManager.currentState.value)

        // NEEDS_CLARIFICATION -> SPEAKING (Asking "Should I use YouTube, Spotify or Brave?")
        assertTrue(VoiceStateManager.transitionTo(VoiceState.SPEAKING))
        assertEquals(VoiceState.SPEAKING, VoiceStateManager.currentState.value)

        // SPEAKING -> FOLLOW_UP_LISTENING (Waiting for user answer)
        assertTrue(VoiceStateManager.transitionTo(VoiceState.FOLLOW_UP_LISTENING))
        assertEquals(VoiceState.FOLLOW_UP_LISTENING, VoiceStateManager.currentState.value)

        // FOLLOW_UP_LISTENING -> PROCESSING -> COMPLETED
        assertTrue(VoiceStateManager.transitionTo(VoiceState.PROCESSING))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.COMPLETED))
        assertEquals(VoiceState.COMPLETED, VoiceStateManager.currentState.value)

        // COMPLETED -> STANDBY
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY))
        assertEquals(VoiceState.STANDBY, VoiceStateManager.currentState.value)
    }
}
