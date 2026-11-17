package com.sam.assistant.voice

import com.sam.assistant.ai.intent.FastCommandEngine
import com.sam.assistant.ai.intent.SessionContextManager
import com.sam.assistant.automation.executor.ActionType
import com.sam.assistant.voice.language.LanguageDetectionManager
import com.sam.assistant.voice.language.SpokenLanguage
import com.sam.assistant.voice.recognition.SpeechBenchmarkManager
import com.sam.assistant.voice.session.SessionTimingConfig
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import com.sam.assistant.voice.vad.VadConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Stage2VoiceEngineTest {

    private lateinit var commandEngine: FastCommandEngine

    @Before
    fun setUp() {
        commandEngine = FastCommandEngine()
        SessionContextManager.clearAll()
        VoiceStateManager.reset()
    }

    // ==========================================
    // 1. Language Detection Tests (EN / HI / Hinglish)
    // ==========================================

    @Test
    fun testLanguageDetectionEnglish() {
        val english1 = LanguageDetectionManager.detectLanguage("Open Brave and search for cybersecurity news")
        assertEquals(SpokenLanguage.ENGLISH, english1)

        val english2 = LanguageDetectionManager.detectLanguage("What is my current battery level?")
        assertEquals(SpokenLanguage.ENGLISH, english2)

        val english3 = LanguageDetectionManager.detectLanguage("Turn on flashlight")
        assertEquals(SpokenLanguage.ENGLISH, english3)
        assertFalse(LanguageDetectionManager.isHindiOrHinglish("Turn on flashlight"))
    }

    @Test
    fun testLanguageDetectionDevanagariHindi() {
        val hindi1 = LanguageDetectionManager.detectLanguage("यूट्यूब खोलो")
        assertEquals(SpokenLanguage.HINDI, hindi1)

        val hindi2 = LanguageDetectionManager.detectLanguage("अलार्म लगाओ")
        assertEquals(SpokenLanguage.HINDI, hindi2)

        val hindi3 = LanguageDetectionManager.detectLanguage("नमस्ते, आप कैसे हैं")
        assertEquals(SpokenLanguage.HINDI, hindi3)
        assertTrue(LanguageDetectionManager.isHindiOrHinglish("नमस्ते, आप कैसे हैं"))
    }

    @Test
    fun testLanguageDetectionRomanHindiAndHinglish() {
        val hinglish1 = LanguageDetectionManager.detectLanguage("WhatsApp kholo aur Rahul ko message bhejo")
        assertTrue(
            hinglish1 == SpokenLanguage.HINGLISH || hinglish1 == SpokenLanguage.HINDI
        )
        assertTrue(LanguageDetectionManager.isHindiOrHinglish("WhatsApp kholo aur Rahul ko message bhejo"))

        val hinglish2 = LanguageDetectionManager.detectLanguage("Brave browser open karo aur python tutorial search karo")
        assertTrue(
            hinglish2 == SpokenLanguage.HINGLISH || hinglish2 == SpokenLanguage.HINDI
        )

        val hindiRoman = LanguageDetectionManager.detectLanguage("torch band karo")
        assertEquals(SpokenLanguage.HINDI, hindiRoman)
    }

    // ==========================================
    // 2. Speech Recognition WER Benchmark Tests
    // ==========================================

    @Test
    fun testWordErrorRateIdenticalStrings() {
        val wer = SpeechBenchmarkManager.computeWer("open youtube", "open youtube")
        assertEquals(0.0f, wer, 0.001f)
    }

    @Test
    fun testWordErrorRateSingleWordSubstitution() {
        // 1 substitution out of 2 words = 50.0% WER
        val wer = SpeechBenchmarkManager.computeWer("open youtube", "open spotify")
        assertEquals(50.0f, wer, 0.001f)
    }

    @Test
    fun testWordErrorRateEmptyHypothesis() {
        val wer = SpeechBenchmarkManager.computeWer("turn on flashlight", "")
        assertEquals(100.0f, wer, 0.001f)
    }

    @Test
    fun testWordErrorRateBothEmpty() {
        val wer = SpeechBenchmarkManager.computeWer("", "")
        assertEquals(0.0f, wer, 0.001f)
    }

    // ==========================================
    // 3. Voice Activity Detection & Adaptive Timing
    // ==========================================

    @Test
    fun testVadTimingParameters() {
        val vadConfig = VadConfig()
        assertEquals(15000L, vadConfig.initialSpeechWaitMs)
        assertEquals(2000L, vadConfig.endOfSpeechSilenceMs)
        assertEquals(5000L, vadConfig.pauseAllowanceMs)
        assertEquals(45000L, vadConfig.maxUtteranceDurationMs)
        assertEquals(16000, vadConfig.sampleRate)
    }

    @Test
    fun testSessionTimingConfigDefaults() {
        val sessionConfig = SessionTimingConfig()
        assertEquals(15000L, sessionConfig.initialSpeechWaitMs)
        assertEquals(2000L, sessionConfig.endOfSpeechSilenceMs)
        assertEquals(5000L, sessionConfig.pauseAllowanceMs)
        assertEquals(15000L, sessionConfig.confirmationTimeoutMs)
        assertEquals(15000L, sessionConfig.followUpWindowMs)
        assertEquals(45000L, sessionConfig.maxUtteranceDurationMs)
    }

    // ==========================================
    // 4. Eleven-State Conversation State Machine
    // ==========================================

    @Test
    fun testAllElevenStatesExist() {
        val expectedStates = listOf(
            VoiceState.INACTIVE,
            VoiceState.STANDBY,
            VoiceState.WAKE_WORD_DETECTED,
            VoiceState.LISTENING,
            VoiceState.PROCESSING,
            VoiceState.NEEDS_CLARIFICATION,
            VoiceState.WAITING_FOR_CONFIRMATION,
            VoiceState.SPEAKING,
            VoiceState.FOLLOW_UP_LISTENING,
            VoiceState.COMPLETED,
            VoiceState.ERROR
        )
        assertEquals(11, VoiceState.values().size)
        assertTrue(VoiceState.values().toList().containsAll(expectedStates))
    }

    @Test
    fun testConfirmationAndFollowUpSessionLifecycle() {
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.WAKE_WORD_DETECTED))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.SPEAKING))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.LISTENING))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.PROCESSING))

        // High-security action -> WAITING_FOR_CONFIRMATION
        assertTrue(VoiceStateManager.transitionTo(VoiceState.WAITING_FOR_CONFIRMATION))
        assertEquals(VoiceState.WAITING_FOR_CONFIRMATION, VoiceStateManager.currentState.value)

        // Spoken prompt: "Are you sure you want to call Rahul?"
        assertTrue(VoiceStateManager.transitionTo(VoiceState.SPEAKING))

        // Open 15-second follow-up listening window
        assertTrue(VoiceStateManager.transitionTo(VoiceState.FOLLOW_UP_LISTENING))
        assertEquals(VoiceState.FOLLOW_UP_LISTENING, VoiceStateManager.currentState.value)

        // User says "Yes" -> PROCESSING -> COMPLETED
        assertTrue(VoiceStateManager.transitionTo(VoiceState.PROCESSING))
        assertTrue(VoiceStateManager.transitionTo(VoiceState.COMPLETED))
        assertEquals(VoiceState.COMPLETED, VoiceStateManager.currentState.value)

        // Return to background standby
        assertTrue(VoiceStateManager.transitionTo(VoiceState.STANDBY))
        assertEquals(VoiceState.STANDBY, VoiceStateManager.currentState.value)
    }

    // ==========================================
    // 5. Hindi, Hinglish & Complex Natural Commands
    // ==========================================

    @Test
    fun testHindiAlarmCommandParsing() {
        val request = commandEngine.parseCommand("kal subah saat baje alarm laga do")
        assertNotNull(request)
        assertEquals(ActionType.SET_ALARM, request?.actionType)
        assertEquals("7", request?.extraParams?.get("hour"))
        assertEquals("0", request?.extraParams?.get("minute"))
    }

    @Test
    fun testHindiTimerCommandParsing() {
        val request = commandEngine.parseCommand("mujhe ek timer lagana hai")
        assertNotNull(request)
        assertEquals(ActionType.CREATE_REMINDER, request?.actionType)
        assertEquals("Timer", request?.payload)
    }

    @Test
    fun testFollowUpOpenAppAndComplexCommand() {
        val request = commandEngine.parseCommand("open whatsapp and show my recent chats")
        assertNotNull(request)
        assertEquals(ActionType.LAUNCH_APP, request?.actionType)
        assertEquals("whatsapp", request?.target)
    }

    @Test
    fun testBraveBrowserSearchCommand() {
        val request = commandEngine.parseCommand("open brave and search for the latest cybersecurity news")
        assertNotNull(request)
        assertEquals(ActionType.SEARCH_WEB, request?.actionType)
        assertEquals("brave", request?.target)
        assertEquals("latest cybersecurity news", request?.payload)
    }
}
