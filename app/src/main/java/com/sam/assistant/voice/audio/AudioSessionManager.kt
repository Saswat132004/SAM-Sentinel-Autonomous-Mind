package com.sam.assistant.voice.audio

import android.content.Context
import android.media.AudioRecord

enum class AudioSessionOwner {
    NONE,
    WAKE_WORD,
    SPEECH_RECOGNITION,
    DIAGNOSTIC
}

class AudioSessionManager(context: Context) {

    companion object {
        const val DEFAULT_SAMPLE_RATE = AudioCaptureManager.SAMPLE_RATE_HZ
        const val DEFAULT_CHANNEL_CONFIG = AudioCaptureManager.CHANNEL_CONFIG
        const val DEFAULT_AUDIO_FORMAT = AudioCaptureManager.AUDIO_FORMAT
    }

    private val captureManager = AudioCaptureManager(context)

    fun setInterruptionListener(listener: ((isInterrupted: Boolean) -> Unit)?) {
        captureManager.setInterruptionListener(listener)
    }

    fun acquireMicrophone(
        owner: AudioSessionOwner,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): AudioRecord? {
        return captureManager.startCapture(owner, sampleRate)
    }

    fun releaseMicrophone(owner: AudioSessionOwner) {
        captureManager.stopCapture(owner)
    }

    fun getActiveOwner(): AudioSessionOwner = captureManager.getActiveOwner()

    fun isRecording(): Boolean = captureManager.isCapturing()
}
