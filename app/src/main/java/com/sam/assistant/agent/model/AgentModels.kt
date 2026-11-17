package com.sam.assistant.agent.model

import com.sam.assistant.automation.core.RiskLevel
import com.sam.assistant.automation.executor.ActionType

enum class TaskStatus {
    PENDING,
    READY,
    PLANNING,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    BLOCKED
}

enum class AgentState {
    IDLE,
    INTERPRETING_GOAL,
    GENERATING_PLAN,
    AWAITING_APPROVAL,
    EXECUTING_GRAPH,
    TASK_PAUSED,
    RECOVERING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class RetryPolicy(
    val maxRetries: Int = 2,
    val backoffDelayMs: Long = 1000L,
    val allowAlternativeTool: Boolean = true
)

data class AgentTask(
    val taskId: String,
    val goalId: String,
    val parentTaskId: String? = null,
    val description: String,
    val actionType: ActionType,
    val toolName: String,
    val inputParameters: Map<String, String> = emptyMap(),
    val dependencies: List<String> = emptyList(), // Prerequisite taskIds
    val priority: Int = 1,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val requiredPermissions: List<String> = emptyList(),
    val requiresApproval: Boolean = false,
    val timeoutMs: Long = 15000L,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val verificationMethod: String = "INTENT_ACCEPTED",
    var status: TaskStatus = TaskStatus.PENDING,
    var executionEvidence: String? = null,
    var errorMessage: String? = null,
    var outputData: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentGoal(
    val goalId: String,
    val rawPrompt: String,
    val mainObjective: String,
    val requiredSubtasks: List<String> = emptyList(),
    val entities: Map<String, String> = emptyMap(),
    val requiredTools: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val missingInfo: List<String> = emptyList(),
    val isSensitive: Boolean = false,
    val requiresApproval: Boolean = false,
    val clarificationPrompt: String? = null
)

data class GoalExecutionResult(
    val goalId: String,
    val status: AgentState,
    val completedTasks: Int,
    val totalTasks: Int,
    val summary: String,
    val evidenceSummary: List<String> = emptyList(),
    val failedTaskReason: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
