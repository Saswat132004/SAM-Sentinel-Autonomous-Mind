package com.sam.assistant.knowledge.conversation

import com.sam.assistant.knowledge.memory.MemoryCategory
import com.sam.assistant.knowledge.memory.PersonalFactEntity
import com.sam.assistant.knowledge.memory.PersonalMemoryEngine

data class ConversationSessionContext(
    var activeTopic: String? = null,
    var activeProject: String? = null,
    var activeStudySubject: String? = null,
    var lastReferencedEntity: String? = null,
    var lastUserUtterance: String? = null,
    var lastAssistantReply: String? = null,
    var turnCount: Int = 0,
    var lastInteractionTimestamp: Long = System.currentTimeMillis()
)

class ConversationalMemoryManager(
    private val memoryEngine: PersonalMemoryEngine
) {
    companion object {
        private const val SESSION_TTL_MS = 300000L // 5 minutes inactivity timeout
    }

    private var sessionContext = ConversationSessionContext()

    fun getSessionContext(): ConversationSessionContext {
        checkSessionExpiration()
        return sessionContext
    }

    fun resetSession() {
        sessionContext = ConversationSessionContext()
    }

    private fun checkSessionExpiration() {
        val elapsed = System.currentTimeMillis() - sessionContext.lastInteractionTimestamp
        if (elapsed > SESSION_TTL_MS) {
            resetSession()
        }
    }

    /**
     * Updates conversational turn context and detects active topics or projects.
     */
    fun recordTurn(userUtterance: String, assistantReply: String) {
        checkSessionExpiration()
        sessionContext.lastUserUtterance = userUtterance
        sessionContext.lastAssistantReply = assistantReply
        sessionContext.turnCount++
        sessionContext.lastInteractionTimestamp = System.currentTimeMillis()

        val lower = userUtterance.lowercase()

        // Detect project topic
        if (lower.contains("ares")) {
            sessionContext.activeProject = "ARES"
            sessionContext.activeTopic = "ARES Architecture"
        }

        // Detect study topic
        if (lower.contains("operating system") || lower.contains("os")) {
            sessionContext.activeStudySubject = "Operating Systems"
            sessionContext.activeTopic = "Operating Systems"
        } else if (lower.contains("computer network") || lower.contains("networking")) {
            sessionContext.activeStudySubject = "Computer Networks"
            sessionContext.activeTopic = "Computer Networks"
        }

        // Track last referenced technical entity
        when {
            lower.contains("database") || lower.contains("db") -> sessionContext.lastReferencedEntity = "database"
            lower.contains("backend") -> sessionContext.lastReferencedEntity = "backend"
            lower.contains("frontend") -> sessionContext.lastReferencedEntity = "frontend"
            lower.contains("algorithm") -> sessionContext.lastReferencedEntity = "algorithm"
            lower.contains("schedule") || lower.contains("time") -> sessionContext.lastReferencedEntity = "schedule"
        }
    }

    /**
     * Resolves a contextual follow-up question using the short-term session context
     * linked to long-term memory facts.
     */
    suspend fun resolveFollowUpQuery(query: String): FollowUpResolution {
        checkSessionExpiration()
        val lower = query.lowercase()

        // 1. Direct or implicit reference to active project (e.g., "What database did I use?", "What is the backend?")
        val targetProject = sessionContext.activeProject
        if (targetProject != null && (lower.contains("database") || lower.contains("db") ||
                    lower.contains("backend") || lower.contains("frontend") || lower.contains("architecture") ||
                    lower.contains("framework") || lower.contains("language") || lower.contains("did i use") ||
                    lower.contains("is it using"))) {

            val projectFacts = memoryEngine.getMemoriesBySubject(targetProject) +
                    memoryEngine.searchMemories(targetProject)

            if (lower.contains("database") || lower.contains("db")) {
                val dbFact = projectFacts.firstOrNull {
                    it.content.contains("database", ignoreCase = true) ||
                            it.content.contains("postgres", ignoreCase = true) ||
                            it.content.contains("redis", ignoreCase = true) ||
                            it.content.contains("mongo", ignoreCase = true) ||
                            it.content.contains("sql", ignoreCase = true)
                }
                if (dbFact != null) {
                    return FollowUpResolution.Resolved(
                        answer = "For your $targetProject project, you specified: ${dbFact.content}.",
                        sourceCitation = "Personal Memory [Fact: ${dbFact.factId}]",
                        relevantFact = dbFact
                    )
                }
            }

            // General project inquiry
            val matchingFact = projectFacts.firstOrNull { fact ->
                val keywords = lower.split(" ").filter { it.length > 3 }
                keywords.any { fact.content.contains(it, ignoreCase = true) }
            } ?: projectFacts.firstOrNull()

            if (matchingFact != null) {
                return FollowUpResolution.Resolved(
                    answer = "Regarding $targetProject: ${matchingFact.content}.",
                    sourceCitation = "Personal Memory [Fact: ${matchingFact.factId}]",
                    relevantFact = matchingFact
                )
            }

            return FollowUpResolution.NotFound(
                reason = "I know you are discussing the $targetProject project, but I don't have a record for this specific detail in your personal memory."
            )
        }

        // 2. Study preference follow-up (e.g., "When do I study?", "What was my study preference?")
        if (lower.contains("when") || lower.contains("study preference") || lower.contains("study time")) {
            val studyMemories = memoryEngine.getMemoriesByCategory(MemoryCategory.STUDY_PREFERENCES)
            if (studyMemories.isNotEmpty()) {
                val fact = studyMemories.first()
                return FollowUpResolution.Resolved(
                    answer = "Based on your study preference: ${fact.content}.",
                    sourceCitation = "Personal Memory [Fact: ${fact.factId}]",
                    relevantFact = fact
                )
            }
        }

        return FollowUpResolution.NotAFollowUp
    }

    /**
     * Summarizes the current conversation session.
     */
    fun summarizeSession(): String {
        checkSessionExpiration()
        if (sessionContext.turnCount == 0) return "No active conversation turns recorded."
        val topic = sessionContext.activeTopic ?: "General exploration"
        val project = sessionContext.activeProject?.let { "Focus Project: $it." } ?: ""
        return "Conversation Session: ${sessionContext.turnCount} turns. Active Topic: $topic. $project"
    }
}

sealed class FollowUpResolution {
    data class Resolved(val answer: String, val sourceCitation: String, val relevantFact: PersonalFactEntity) : FollowUpResolution()
    data class NotFound(val reason: String) : FollowUpResolution()
    object NotAFollowUp : FollowUpResolution()
}
