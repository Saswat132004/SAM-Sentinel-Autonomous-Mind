package com.sam.assistant.voice.recognition

data class SpeechBenchmarkMetrics(
    val engineName: String,
    val language: String,
    val wordErrorRatePercent: Float,
    val commandAccuracyPercent: Float,
    val averageLatencyMs: Long,
    val ramUsageMb: Long,
    val estimatedBatteryDrainPercentPerHour: Float,
    val sampleCount: Int
)

interface SamSpeechEngine {
    val engineName: String
    val isReady: Boolean
    val supportedLanguages: List<String>
    
    suspend fun initialize(): Boolean
    fun startListening(onResult: (RecognitionResult) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun release()
    fun getBenchmarkMetrics(): SpeechBenchmarkMetrics?
}
