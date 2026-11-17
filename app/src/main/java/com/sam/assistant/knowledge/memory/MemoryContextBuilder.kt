package com.sam.assistant.knowledge.memory

class MemoryContextBuilder(private val retriever: MemoryRetriever) {

    suspend fun buildContextForTopic(topic: String, limit: Int = 5): String {
        val matches = retriever.search(topic)
        if (matches.isEmpty()) return ""

        val sb = StringBuilder("=== Long-Term Personal Memory [Topic: $topic] ===\n")
        matches.take(limit).forEach { fact ->
            sb.append("- [${fact.category}] ${fact.subject}: \"${fact.content}\" (Source: ${fact.sourceReference})\n")
        }
        return sb.toString()
    }

    suspend fun buildAllPreferencesContext(): String {
        val studyPrefs = retriever.getMemoriesByCategory(MemoryCategory.STUDY_PREFERENCES)
        val appPrefs = retriever.getMemoriesByCategory(MemoryCategory.APPLICATION_PREFERENCES)
        val personalPrefs = retriever.getMemoriesByCategory(MemoryCategory.PERSONAL_PREFERENCES)

        val all = studyPrefs + appPrefs + personalPrefs
        if (all.isEmpty()) return "No preferences saved."

        return all.joinToString("; ") { "${it.subject}: ${it.content}" }
    }
}
