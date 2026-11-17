package com.sam.assistant.ai.cognitive.entity

import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.normalizer.LanguageNormalizer
import java.util.regex.Pattern

object EntityExtractor {

    private val knownApps = listOf(
        "youtube music", "yt music", "youtube", "brave browser", "brave",
        "google chrome", "chrome", "whatsapp", "spotify", "camera", "gallery",
        "settings", "maps", "clock", "calendar", "calculator", "messages", "phone"
    )

    /**
     * Extracts strongly-typed entities from normalized or raw utterance based on classified intent.
     */
    fun extract(rawUtterance: String, intent: CognitiveIntent): CognitiveEntities {
        val normalized = LanguageNormalizer.normalize(rawUtterance)

        val date = LanguageNormalizer.normalizeRelativeDate(normalized)
        val time = LanguageNormalizer.normalizeTime(normalized)
        val ordinal = LanguageNormalizer.extractOrdinal(normalized)
        val appName = extractAppName(normalized)
        val contactName = extractContactName(rawUtterance, normalized, intent)
        val searchQuery = extractSearchQuery(rawUtterance, normalized, intent)
        val documentName = extractDocumentName(normalized)
        val subject = extractReminderSubject(rawUtterance, normalized, intent)
        val number = extractNumber(normalized) ?: time?.split(":")?.firstOrNull()?.toIntOrNull()

        val actionParams = mutableMapOf<String, String>()

        // Device control state
        LanguageNormalizer.normalizeDeviceControl(normalized)?.let { (target, state) ->
            actionParams["target"] = target
            actionParams["state"] = state
        }

        // Message text
        extractMessageText(normalized, intent)?.let {
            actionParams["message"] = it
        }

        if (subject != null) {
            actionParams["subject"] = subject
        }

        if (appName != null) {
            actionParams["app"] = appName
        }

        return CognitiveEntities(
            appName = appName,
            contactName = contactName,
            date = date,
            time = time,
            searchQuery = searchQuery,
            documentName = documentName,
            subject = subject,
            number = number,
            ordinal = ordinal,
            actionParameters = actionParams
        )
    }

    private fun extractAppName(input: String): String? {
        for (app in knownApps) {
            if (input.contains(Regex("\\b$app\\b"))) {
                return when (app) {
                    "brave browser" -> "brave"
                    "google chrome" -> "chrome"
                    "yt music" -> "youtube music"
                    else -> app
                }
            }
        }
        return null
    }

    private fun extractContactName(rawInput: String, normalized: String, intent: CognitiveIntent): String? {
        if (intent != CognitiveIntent.MAKE_CALL && intent != CognitiveIntent.SEND_MESSAGE) return null

        // 1. "call [contact]" / "dial [contact]"
        val enCall = Pattern.compile("^(?:call|dial|text|message)\\s+([a-z0-9\\s+]+?)(?:\\s+(?:and|please|saying|that).*)?$")
        val enMatcher = enCall.matcher(normalized)
        if (enMatcher.matches()) {
            val name = enMatcher.group(1)?.trim()
            if (!name.isNullOrBlank() && name != "someone" && name != "him" && name != "her") {
                return formatName(name)
            }
        }

        // 2. Hinglish / Hindi: "[contact] ko call lagao / phone karo / message bhejo"
        val hiCall = Pattern.compile("^([a-z0-9\\s+]+?)\\s+ko\\s+(?:call|phone|message|msg)\\s+(?:lagao|karo|milao|bhejo|bhejne).*$")
        val hiMatcher = hiCall.matcher(normalized)
        if (hiMatcher.matches()) {
            val name = hiMatcher.group(1)?.trim()
            if (!name.isNullOrBlank() && name != "kisi") {
                return formatName(name)
            }
        }

        // 3. "send message to [contact]"
        val sendTo = Pattern.compile("^(?:send\\s+(?:a\\s+)?(?:message|whatsapp|sms)\\s+to\\s+)([a-z0-9\\s+]+?)(?:\\s+saying.*)?$")
        val sendMatcher = sendTo.matcher(normalized)
        if (sendMatcher.matches()) {
            val name = sendMatcher.group(1)?.trim()
            if (!name.isNullOrBlank()) {
                return formatName(name)
            }
        }

        return null
    }

    private fun extractSearchQuery(rawInput: String, normalized: String, intent: CognitiveIntent): String? {
        var query: String? = null

        // Format 1: English prefix ("search for [query]", "search [query] on [browser]")
        val p1 = Pattern.compile("(?:search\\s+for|search|khojo)[,\\s]+(.+?)(?:\\s+(?:on|in|using|via)\\s+[a-z]+|\\s*karo|\\s*khojo)?$")
        val m1 = p1.matcher(normalized)
        if (m1.find()) {
            var q = m1.group(1)?.trim() ?: ""
            q = q.replace(Regex("(?i)^(?:for\\s+)?(?:the\\s+)?"), "").trim()
            q = q.replace(Regex("(?i)[,\\s]*(?:and|aur)\\s*play.*$"), "").trim()
            q = q.replace(Regex("(?i)\\s*(?:karo|khojo)$"), "").trim()
            if (q.isNotBlank()) query = q
        }

        // Format 2: Hindi/Hinglish suffix ("[query] search karo", "[query] khojo")
        if (query == null) {
            val p2 = Pattern.compile("^(?:.*?(?:aur|and|,))?\\s*(.+?)\\s+(?:search\\s+karo|khojo|search)$")
            val m2 = p2.matcher(normalized)
            if (m2.find()) {
                var q = m2.group(1)?.trim() ?: ""
                q = q.replace(Regex("(?i)^(?:for\\s+)?(?:the\\s+)?"), "").trim()
                if (q.isNotBlank()) query = q
            }
        }

        if (query != null) {
            // Restore raw casing from original utterance if available
            val rawIdx = rawInput.indexOf(query, ignoreCase = true)
            if (rawIdx != -1) {
                return rawInput.substring(rawIdx, rawIdx + query.length).trim()
            }
            return query
        }

        return null
    }

    private fun extractDocumentName(input: String): String? {
        val p = Pattern.compile("(?:document|file|pdf)\\s+([a-z0-9_.-]+)")
        val m = p.matcher(input)
        if (m.find()) {
            return m.group(1)?.trim()
        }
        return null
    }

    private fun extractReminderSubject(rawInput: String, normalized: String, intent: CognitiveIntent): String? {
        if (intent != CognitiveIntent.CREATE_REMINDER && intent != CognitiveIntent.SET_TIMER) return null

        // Example: "Kal subah saat baje Python padhne ka reminder laga do."
        val hiReminder = Pattern.compile("(?:baje\\s+)?(.+?)\\s+ka\\s+reminder")
        val hiMatcher = hiReminder.matcher(normalized)
        if (hiMatcher.find()) {
            val rawSub = hiMatcher.group(1)?.trim() ?: ""
            var cleaned = rawSub
                .replace(Regex("(?i)\\b(?:kal|aaj|parso|subah|shaam|raat|dopahar|saat|paanch|chhe|baje)\\b"), "")
                .trim()

            val rawIdx = rawInput.indexOf(cleaned, ignoreCase = true)
            if (rawIdx != -1) {
                cleaned = rawInput.substring(rawIdx, rawIdx + cleaned.length)
            }

            if (cleaned.contains("padhne", ignoreCase = true)) {
                val topic = cleaned.replace(Regex("(?i)\\bpadhne(?:\\s+ka)?\\b"), "").trim()
                return "study $topic".trim()
            }
            if (cleaned.isNotBlank()) return cleaned
        }

        // English: "remind me to [subject] at/in..."
        val enReminder = Pattern.compile("remind\\s+me\\s+to\\s+(.+?)(?:\\s+(?:at|in|on|tomorrow|today).*)?$")
        val enMatcher = enReminder.matcher(normalized)
        if (enMatcher.find()) {
            val sub = enMatcher.group(1)?.trim()
            if (!sub.isNullOrBlank()) {
                val rawIdx = rawInput.indexOf(sub, ignoreCase = true)
                if (rawIdx != -1) {
                    return rawInput.substring(rawIdx, rawIdx + sub.length)
                }
                return sub
            }
        }

        return null
    }

    private fun extractMessageText(input: String, intent: CognitiveIntent): String? {
        if (intent != CognitiveIntent.SEND_MESSAGE) return null

        val p = Pattern.compile("saying\\s+(.+)$")
        val m = p.matcher(input)
        if (m.find()) {
            return m.group(1)?.trim()
        }
        return null
    }

    private fun extractNumber(input: String): Int? {
        val p = Pattern.compile("\\b(\\d+)\\b")
        val m = p.matcher(input)
        if (m.find()) {
            return m.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun formatName(name: String): String {
        return name.split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
    }
}
