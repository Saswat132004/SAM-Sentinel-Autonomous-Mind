package com.sam.assistant.automation.executor

import com.sam.assistant.ai.planner.TaskPlan

data class ActionRequest(
    val actionType: ActionType,
    val target: String = "", // e.g., app name, contact name, setting name
    val payload: String = "", // e.g., message text, volume delta, memory key/value, query
    val extraParams: Map<String, String> = emptyMap(),
    val rawCommand: String = "",
    val requiresConfirmation: Boolean = actionType.isSensitive,
    val confirmationPrompt: String = ""
)

sealed class ExecutionResult {
    data class Success(
        val message: String,
        val spokenResponse: String = message,
        val details: Map<String, String> = emptyMap()
    ) : ExecutionResult()

    data class PartialSuccess(
        val message: String,
        val spokenResponse: String = message,
        val completedSteps: Int,
        val totalSteps: Int,
        val failedStepReason: String
    ) : ExecutionResult()

    data class Failed(
        val userExplanation: String,
        val technicalError: String,
        val failingComponent: String,
        val suggestedAlternative: String? = null,
        val spokenError: String = userExplanation
    ) : ExecutionResult()

    data class NeedsClarification(
        val prompt: String,
        val contextKey: String,
        val options: List<String> = emptyList(),
        val pendingPlan: TaskPlan? = null
    ) : ExecutionResult()

    data class NeedsConfirmation(
        val prompt: String,
        val request: ActionRequest,
        val pendingPlan: TaskPlan? = null
    ) : ExecutionResult()

    data class PermissionRequired(
        val permission: String,
        val userExplanation: String
    ) : ExecutionResult()

    // Backwards-compatibility constructor for legacy calls
    data class Error(
        val errorMessage: String,
        val spokenError: String = errorMessage
    ) : ExecutionResult()
}
