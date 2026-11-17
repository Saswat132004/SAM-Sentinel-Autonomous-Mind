package com.sam.assistant.voice.wakeword

data class WakeWordConfig(
    val wakePhrase: String = "Hey SAM",
    val sensitivity: Float = 0.75f,            // 0.0 (least sensitive) to 1.0 (most sensitive)
    val sampleRate: Int = 16000,
    val baseEnergyThreshold: Double = 1600.0,   // Adjusted by sensitivity factor
    val minAcousticBurstFrames: Int = 3,       // ~60ms
    val maxAcousticBurstFrames: Int = 16,      // ~320ms
    val interSyllableGapFrames: Int = 8,       // ~160ms allowable pause between "Hey" and "SAM"
    val cooldownPeriodMs: Long = 1800L,        // Debounce lock out after successful activation
    val enableAcousticCadence: Boolean = true,
    val voskGrammarRule: String = "[\"hey sam\", \"[unk]\"]"
) {
    /**
     * Dynamic threshold scaled by user sensitivity setting (0.0 to 1.0).
     * High sensitivity lowers threshold; low sensitivity raises threshold.
     */
    val effectiveEnergyThreshold: Double
        get() {
            val scale = (1.5 - (sensitivity * 0.9)).coerceIn(0.6, 2.0)
            return baseEnergyThreshold * scale
        }
}
