package com.sam.assistant.voice.language

import java.util.Locale

enum class SpokenLanguage(val code: String, val displayName: String, val ttsLocale: Locale) {
    ENGLISH("en-IN", "Indian English", Locale("en", "IN")),
    HINDI("hi-IN", "Hindi", Locale("hi", "IN")),
    HINGLISH("hi-en", "Hinglish", Locale("en", "IN"))
}

object LanguageDetectionManager {

    private val devanagariRange = '\u0900'..'\u097F'

    // Common Hindi/Hinglish vocabulary tokens in Roman script
    private val hindiLexicon = setOf(
        "kholo", "khol", "karo", "kar", "bhejo", "bhej", "lagao", "laga", "bajao", "chalao",
        "jalao", "bujhao", "band", "chalu", "kitna", "kitni", "kitne", "hai", "hain", "hoon",
        "kaise", "kya", "kyun", "kab", "kahan", "aur", "fir", "phir", "se", "ko", "mera", "meri",
        "mere", "mujhe", "humko", "aap", "tum", "bhai", "yaar", "gaana", "sangeet", "aawaz",
        "samay", "baje", "subah", "shaam", "raat", "kal", "aaj", "parso", "theek", "sahi", "haan", "nahi"
    )

    fun detectLanguage(input: String): SpokenLanguage {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        // 1. Check Devanagari script
        val hasDevanagari = trimmed.any { it in devanagariRange }
        if (hasDevanagari) {
            return SpokenLanguage.HINDI
        }

        // 2. Tokenize Roman script words and analyze vocabulary
        val tokens = trimmed.lowercase().split(Regex("[\\s,;!?.]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return SpokenLanguage.ENGLISH

        var hindiTokenCount = 0
        var englishTokenCount = 0

        for (token in tokens) {
            if (hindiLexicon.contains(token)) {
                hindiTokenCount++
            } else {
                englishTokenCount++
            }
        }

        val total = tokens.size.toFloat()
        val hindiRatio = hindiTokenCount / total

        return when {
            hindiRatio >= 0.5f -> SpokenLanguage.HINDI
            hindiRatio in 0.15f..0.5f -> SpokenLanguage.HINGLISH
            else -> SpokenLanguage.ENGLISH
        }
    }

    fun isHindiOrHinglish(text: String): Boolean {
        val lang = detectLanguage(text)
        return lang == SpokenLanguage.HINDI || lang == SpokenLanguage.HINGLISH
    }

    fun getRecommendedTtsLocale(text: String): Locale {
        return detectLanguage(text).ttsLocale
    }
}
