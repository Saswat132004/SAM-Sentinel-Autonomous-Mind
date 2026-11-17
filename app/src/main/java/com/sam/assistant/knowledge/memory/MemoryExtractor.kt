package com.sam.assistant.knowledge.memory

import java.util.UUID

data class ExtractedMemory(
    val factId: String = "fact_" + UUID.randomUUID().toString().take(8),
    val subject: String,
    val content: String,
    val category: MemoryCategory,
    val tags: List<String>,
    val confidence: Float = 1.0f
)

class MemoryExtractor(private val classifier: MemoryClassifier = MemoryClassifier()) {

    fun extract(statement: String): ExtractedMemory? {
        val trimmed = statement.trim()
        val lower = trimmed.lowercase()

        // Strip leading commands like "remember that", "remember", "yaad rakhna ki", "save that"
        val content = when {
            lower.startsWith("remember that ") -> trimmed.substring("remember that ".length).trim()
            lower.startsWith("remember ") -> trimmed.substring("remember ".length).trim()
            lower.startsWith("yaad rakhna ki ") -> trimmed.substring("yaad rakhna ki ".length).trim()
            lower.startsWith("yaad rakho ") -> trimmed.substring("yaad rakho ".length).trim()
            lower.startsWith("save that ") -> trimmed.substring("save that ".length).trim()
            lower.startsWith("save ") -> trimmed.substring("save ".length).trim()
            else -> trimmed
        }

        if (content.isBlank()) return null

        val subject = extractSubject(content)
        val category = classifier.classify(content, subject)
        val tags = extractTags(content, subject)

        return ExtractedMemory(
            subject = subject,
            content = content,
            category = category,
            tags = tags,
            confidence = 1.0f
        )
    }

    private fun extractSubject(content: String): String {
        val lower = content.lowercase()

        return when {
            lower.contains("ares") -> "ARES Project"
            lower.contains("study") && (lower.contains("evening") || lower.contains("morning") || lower.contains("night")) -> "Study Schedule"
            lower.contains("browser") || lower.contains("brave") || lower.contains("chrome") -> "Browser Preference"
            lower.contains("database") -> "Database Architecture"
            lower.contains("exam") || lower.contains("syllabus") -> "Exam Preparation"
            lower.contains("favorite") || lower.contains("favourite") -> {
                val match = Regex("(?:favorite|favourite)\\s+([a-zA-Z0-9]+)").find(lower)
                match?.groups?.get(1)?.value?.replaceFirstChar { it.uppercase() } ?: "Personal Preference"
            }
            content.contains(" is ") -> {
                content.substringBefore(" is ").trim().replaceFirstChar { it.uppercase() }
            }
            else -> {
                val words = content.split(" ").take(4).joinToString(" ")
                words.replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun extractTags(content: String, subject: String): List<String> {
        val tags = mutableSetOf<String>()
        val combined = "$subject $content".lowercase()
        val tokens = combined.split(Regex("[\\s,.:;!?()\\[\\]\"]+")).filter { it.length > 2 }

        val commonStopWords = setOf("that", "this", "with", "from", "have", "been", "uses", "what", "which", "will", "your")
        for (token in tokens) {
            if (!commonStopWords.contains(token)) {
                tags.add(token)
            }
        }
        return tags.take(8).toList()
    }
}
