package com.sam.assistant.ai.cognitive.reasoning

import android.util.Log
import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.models.StructuredPlanProposal
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType

object ReasoningEngine {

    private const val TAG = "ReasoningEngine"

    /**
     * Determines whether an utterance requires multi-step structured reasoning.
     */
    fun requiresStructuredReasoning(utterance: String): Boolean {
        val lower = utterance.lowercase()
        val complexIndicators = listOf(
            "and explain whether", "and explain if", "search its syllabus",
            "find a good", "compare", "evaluate", "step by step",
            "first search", "then analyze", "aur samjhao", "aur dekho"
        )
        return complexIndicators.any { lower.contains(it) } ||
                (lower.contains("and") && lower.contains("search") && lower.contains("explain"))
    }

    /**
     * Generates a structured multi-step plan proposal.
     * Note: Private internal reasoning steps are kept strictly internal; only the clean
     * objective, subtasks, missing info, and concise explanation are surfaced.
     */
    fun produceStructuredPlan(utterance: String, entities: CognitiveEntities): StructuredPlanProposal {
        Log.d(TAG, "Decomposing complex instruction into structured plan: \"$utterance\"")
        val lower = utterance.lowercase()

        // Example Case: "Find a good Python course, search its syllabus and explain whether it is suitable for beginners."
        if (lower.contains("python course") || (lower.contains("course") && lower.contains("syllabus"))) {
            val objective = "Evaluate Python beginner course and analyze curriculum suitability"
            val subtasks = listOf(
                "Search web for top beginner Python course recommendations",
                "Extract course syllabus and foundational programming topics",
                "Analyze coverage of beginner concepts (syntax, control flow, functions, basic data structures)",
                "Synthesize recommendation and explain suitability"
            )
            val requiredInfo = listOf("Course domain (Python)", "Target audience (Beginners)", "Curriculum syllabus")
            val missingInfo = listOf("Preferred learning platform (e.g. YouTube, Coursera, freeCodeCamp; defaulting to open web)")
            val conciseExplanation = "I will find a top-rated Python beginner course, check its syllabus for fundamental concepts like loops and functions, and give you a clear beginner suitability assessment."

            val executableActions = listOf(
                ActionRequest(
                    actionType = ActionType.SEARCH_WEB,
                    target = "brave",
                    payload = "best Python beginner course syllabus review",
                    rawCommand = utterance
                )
            )

            return StructuredPlanProposal(
                objective = objective,
                subtasks = subtasks,
                requiredInfo = requiredInfo,
                missingInfo = missingInfo,
                conciseUserExplanation = conciseExplanation,
                executableActions = executableActions
            )
        }

        // Generic complex task decomposition
        val objective = "Execute multi-step task: ${entities.searchQuery ?: utterance}"
        val subtasks = listOf(
            "Gather prerequisite information via web / device query",
            "Process and analyze retrieved content",
            "Deliver synthesized summary to user"
        )
        val conciseExplanation = "I will search the required details and analyze the outcome for you."

        val actions = listOf(
            ActionRequest(
                actionType = ActionType.SEARCH_WEB,
                target = "brave",
                payload = entities.searchQuery ?: utterance,
                rawCommand = utterance
            )
        )

        return StructuredPlanProposal(
            objective = objective,
            subtasks = subtasks,
            requiredInfo = listOf("Target query"),
            missingInfo = emptyList(),
            conciseUserExplanation = conciseExplanation,
            executableActions = actions
        )
    }
}
