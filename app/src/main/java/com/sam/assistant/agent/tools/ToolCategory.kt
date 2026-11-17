package com.sam.assistant.agent.tools

enum class ToolCategory {
    ANDROID,
    RESEARCH,
    INTELLIGENCE,
    CYBERSECURITY
}

sealed class ToolExecutionResult {
    data class Success(
        val resultData: Map<String, String> = emptyMap(),
        val evidence: String,
        val userMessage: String
    ) : ToolExecutionResult()

    data class Failed(
        val reason: String,
        val isRetryable: Boolean = true,
        val failingTool: String,
        val suggestedAlternative: String? = null
    ) : ToolExecutionResult()

    data class RequiresApproval(
        val prompt: String,
        val parameters: Map<String, String> = emptyMap()
    ) : ToolExecutionResult()

    data class PermissionRequired(
        val permission: String,
        val message: String
    ) : ToolExecutionResult()
}
