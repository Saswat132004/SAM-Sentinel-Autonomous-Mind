package com.sam.assistant.ai.cognitive.normalizer

import java.util.regex.Pattern

object LanguageNormalizer {

    private val hindiNumberMap = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "chaar" to 4, "paanch" to 5,
        "chhe" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "baarah" to 12
    )

    private val ordinalMap = mapOf(
        "first" to 1, "1st" to 1, "pehla" to 1, "pehli" to 1, "first one" to 1, "the first one" to 1,
        "second" to 2, "2nd" to 2, "doosra" to 2, "doosri" to 2, "second one" to 2, "the second one" to 2,
        "third" to 3, "3rd" to 3, "teesra" to 3, "teesri" to 3, "third one" to 3, "the third one" to 3,
        "fourth" to 4, "4th" to 4, "chautha" to 4, "chauthi" to 4, "fourth one" to 4, "the fourth one" to 4,
        "fifth" to 5, "5th" to 5, "paanchwa" to 5, "paanchwi" to 5, "the fifth one" to 5,
        "last" to -1, "aakhri" to -1, "last one" to -1, "the last one" to -1
    )

    /**
     * Normalizes spoken text across English, Hindi, and Hinglish.
     */
    fun normalize(input: String): String {
        var str = input.trim().lowercase()

        // 1. Remove polite conversational prefixes and greetings
        val prefixRegex = Regex("^(?:hey\\s+sam|ok\\s+sam|okay\\s+sam|sam|namaste\\s+sam|arre\\s+sam|sun\\s+na|sun\\s+bhai|sun\\s+yaar|bhai\\s+zara|do\\s+one\\s+thing|could\\s+you\\s+please|can\\s+you\\s+please|can\\s+you|please\\s+kindly|kindly|please|kripya|zara|i\\s+want\\s+to|i'd\\s+like\\s+to|mujhe\\s+chahiye)[,\\s]+")
        str = str.replace(prefixRegex, "").trim()
        str = str.replace(prefixRegex, "").trim()

        // 2. Remove trailing polite markers and colloquial fillers
        str = str.replace(Regex("(?i)[,\\s]+(?:please|kripya|na|bhai|yaar|batao|karo\\s+na)$"), "").trim()

        // 3. Remove unnecessary punctuation while retaining colons for time (e.g. 7:00)
        str = str.replace(Regex("[?!.,;]+$"), "").trim()

        return str
    }

    /**
     * Resolves ordinal references (e.g. "the second one", "doosra", "2nd") to an integer index (1-based, or -1 for last).
     */
    fun extractOrdinal(input: String): Int? {
        val cleaned = normalize(input).trim()
        for ((phrase, value) in ordinalMap) {
            if (cleaned == phrase || cleaned.contains(Regex("\\b$phrase\\b"))) {
                return value
            }
        }
        return null
    }

    /**
     * Resolves Hindi numeral tokens ("saat", "paanch", etc.) into standard integers.
     */
    fun parseHindiNumber(token: String): Int? {
        val lower = token.trim().lowercase()
        return hindiNumberMap[lower] ?: lower.toIntOrNull()
    }

    /**
     * Standardizes relative dates to canonical string representations: "today", "tomorrow", "day_after_tomorrow".
     */
    fun normalizeRelativeDate(input: String): String? {
        val lower = input.lowercase()
        return when {
            lower.contains(Regex("\\b(?:kal|tomorrow)\\b")) -> "tomorrow"
            lower.contains(Regex("\\b(?:aaj|today)\\b")) -> "today"
            lower.contains(Regex("\\b(?:parso|day after tomorrow)\\b")) -> "day_after_tomorrow"
            else -> null
        }
    }

    /**
     * Standardizes time strings (e.g. "subah saat baje", "7 am", "6:30 pm", "shaam 8 baje") into 24-hr format (HH:mm).
     */
    fun normalizeTime(input: String): String? {
        val lower = input.lowercase()

        // Pattern 1: Hindi time ("subah saat baje", "shaam 8 baje", "dopahar 2:30 baje")
        val hindiPattern = Pattern.compile("(?:(subah|shaam|raat|dopahar)\\s+)?([a-z0-9]+)(?::(\\d{2}))?\\s*baje")
        val hindiMatcher = hindiPattern.matcher(lower)
        if (hindiMatcher.find()) {
            val period = hindiMatcher.group(1) ?: "subah"
            val hourToken = hindiMatcher.group(2) ?: "7"
            val minToken = hindiMatcher.group(3) ?: "00"
            var hour = parseHindiNumber(hourToken) ?: 7
            val min = minToken.toIntOrNull() ?: 0

            if ((period == "shaam" || period == "raat") && hour < 12) hour += 12
            if (period == "dopahar" && hour < 12 && hour != 12) hour += 12

            return String.format("%02d:%02d", hour, min)
        }

        // Pattern 2: Standard English 12h / 24h ("7 am", "6:30 pm", "at 7:00")
        val enPattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        val enMatcher = enPattern.matcher(lower)
        if (enMatcher.find()) {
            var hour = enMatcher.group(1)?.toIntOrNull() ?: return null
            val min = enMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = enMatcher.group(3)

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            return String.format("%02d:%02d", hour, min)
        }

        return null
    }

    /**
     * Normalizes device control expressions to standard targets and actions.
     */
    fun normalizeDeviceControl(input: String): Pair<String, String>? {
        val lower = input.lowercase()

        // Flashlight / Torch
        if (lower.contains("torch") || lower.contains("flashlight")) {
            val isOff = lower.contains("off") || lower.contains("disable") ||
                    lower.contains("band") || lower.contains("bujhao")
            val action = if (isOff) "off" else "on"
            return Pair("torch", action)
        }

        // Volume
        if (lower.contains("volume") || lower.contains("aawaz")) {
            val action = when {
                lower.contains("up") || lower.contains("raise") || lower.contains("increase") || lower.contains("badhao") -> "up"
                lower.contains("down") || lower.contains("lower") || lower.contains("decrease") || lower.contains("kam") -> "down"
                lower.contains("mute") || lower.contains("chup") || lower.contains("band") -> "mute"
                lower.contains("unmute") -> "unmute"
                else -> "adjust"
            }
            return Pair("volume", action)
        }

        // Wi-Fi
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return Pair("wifi", "open")
        }

        // Bluetooth
        if (lower.contains("bluetooth")) {
            return Pair("bluetooth", "open")
        }

        return null
    }
}
