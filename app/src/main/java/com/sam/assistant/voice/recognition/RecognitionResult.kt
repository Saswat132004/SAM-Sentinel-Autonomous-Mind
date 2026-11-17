package com.sam.assistant.voice.recognition

data class RecognitionResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val rawHypothesis: String = text,
    val durationMs: Long = 0L,
    val engineUsed: String = "Vosk Offline ASR"
) {
    val isCleanSpeech: Boolean
        get() = text.isNotBlank() && confidence >= 0.4f
}
