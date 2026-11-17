package com.sam.assistant.voice

import com.sam.assistant.voice.vad.VadConfig
import com.sam.assistant.voice.vad.VoiceActivityDetector
import org.junit.Assert.*
import org.junit.Test

class VoiceActivityDetectorTest {

    @Test
    fun testVadSpeechDetection() {
        var speechStarted = false
        var speechEnded = false

        val vad = VoiceActivityDetector(
            config = VadConfig(endOfSpeechSilenceMs = 100L, energyThresholdOffset = 10.0),
            onSpeechStart = { speechStarted = true },
            onSpeechEnd = { speechEnded = true }
        )

        // 1. Process silence frames (low amplitude)
        val silenceBuffer = ShortArray(320) { 10 }
        for (i in 0 until 5) {
            vad.processFrame(silenceBuffer, silenceBuffer.size)
        }
        assertFalse(speechStarted)

        // 2. Process high-amplitude speech frames (e.g. amplitude 3000)
        val speechBuffer = ShortArray(320) { 3000 }
        for (i in 0 until 5) {
            vad.processFrame(speechBuffer, speechBuffer.size)
        }
        assertTrue(speechStarted)
        assertTrue(vad.isSpeechActive())
    }

    @Test
    fun testVadReset() {
        val vad = VoiceActivityDetector()
        vad.reset()
        assertFalse(vad.isSpeechActive())
    }
}
