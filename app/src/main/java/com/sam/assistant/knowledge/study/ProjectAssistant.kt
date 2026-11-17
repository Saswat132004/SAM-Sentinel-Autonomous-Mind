package com.sam.assistant.knowledge.study

import com.sam.assistant.knowledge.memory.MemoryCategory
import com.sam.assistant.knowledge.memory.PersonalFactEntity
import com.sam.assistant.knowledge.memory.PersonalMemoryEngine

data class ProjectSummary(
    val projectName: String,
    val factsCount: Int,
    val architectureDetails: List<String>,
    val decisions: List<String>,
    val fullSummary: String
)

class ProjectAssistant(private val memoryEngine: PersonalMemoryEngine) {

    /**
     * Retrieves all user-approved facts and architectural details for a specific project.
     */
    suspend fun getProjectDetails(projectName: String): ProjectSummary {
        val facts = memoryEngine.getMemoriesBySubject(projectName) +
                memoryEngine.searchMemories(projectName).filter { it.category == MemoryCategory.PROJECT_INFORMATION.name }

        val uniqueFacts = facts.distinctBy { it.factId }

        val architectureDetails = mutableListOf<String>()
        val decisions = mutableListOf<String>()

        uniqueFacts.forEach { fact ->
            val lower = fact.content.lowercase()
            if (lower.contains("database") || lower.contains("architecture") || lower.contains("backend") || lower.contains("api")) {
                architectureDetails.add("• [${fact.subject}] ${fact.content}")
            } else {
                decisions.add("• ${fact.content}")
            }
        }

        val summaryText = if (uniqueFacts.isNotEmpty()) {
            "Project \"$projectName\" has ${uniqueFacts.size} verified memory facts. " +
                    uniqueFacts.joinToString(" ") { it.content }
        } else {
            "No verified project memory recorded for \"$projectName\"."
        }

        return ProjectSummary(
            projectName = projectName,
            factsCount = uniqueFacts.size,
            architectureDetails = architectureDetails,
            decisions = decisions,
            fullSummary = summaryText
        )
    }

    /**
     * Stores a new user-approved architecture or implementation decision.
     */
    suspend fun recordProjectDecision(
        projectName: String,
        decisionContent: String,
        source: String = "User Project Update"
    ): Boolean {
        val statement = "Remember that $projectName uses $decisionContent"
        val res = memoryEngine.saveApprovedMemory(
            statement = statement,
            isExplicitRequest = true,
            isUserApproved = true,
            sourceReference = source
        )
        return res is com.sam.assistant.knowledge.memory.SaveMemoryResult.Success
    }
}
