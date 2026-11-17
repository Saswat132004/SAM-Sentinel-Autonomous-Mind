package com.sam.assistant.agent.execution

import com.sam.assistant.agent.graph.TaskGraphManager
import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.TaskStatus
import com.sam.assistant.agent.tools.ToolExecutionResult
import kotlinx.coroutines.delay

sealed class RecoveryAction {
    data class Retry(val delayMs: Long) : RecoveryAction()
    data class SubstituteTool(val alternativeTask: AgentTask) : RecoveryAction()
    data class GracefulDegrade(val warningMessage: String) : RecoveryAction()
    data class Abort(val reason: String) : RecoveryAction()
}

class RecoveryEngine {

    private val retryCounts = mutableMapOf<String, Int>()

    fun determineRecovery(
        task: AgentTask,
        failureResult: ToolExecutionResult.Failed,
        graphManager: TaskGraphManager
    ): RecoveryAction {
        val currentRetries = retryCounts[task.taskId] ?: 0

        // 1. Check Retry with Backoff
        if (failureResult.isRetryable && currentRetries < task.retryPolicy.maxRetries) {
            retryCounts[task.taskId] = currentRetries + 1
            val delayMs = task.retryPolicy.backoffDelayMs * (currentRetries + 1)
            return RecoveryAction.Retry(delayMs)
        }

        // 2. Check Alternative Tool Substitution
        if (task.retryPolicy.allowAlternativeTool && failureResult.suggestedAlternative != null) {
            val altTool = failureResult.suggestedAlternative
            val altParams = task.inputParameters.toMutableMap()
            altParams["target"] = altTool

            val altTask = task.copy(
                taskId = "${task.taskId}_alt",
                description = "${task.description} (Fallback: $altTool)",
                inputParameters = altParams,
                status = TaskStatus.READY
            )
            return RecoveryAction.SubstituteTool(altTask)
        }

        // 3. Graceful degradation for low-priority non-critical verification tasks
        if (task.priority > 2 && !task.requiresApproval) {
            return RecoveryAction.GracefulDegrade("Non-critical step failed: ${failureResult.reason}. Continuing plan.")
        }

        // 4. Critical failure, abort
        return RecoveryAction.Abort("Task [${task.taskId}] failed and could not be recovered: ${failureResult.reason}")
    }

    fun reset() {
        retryCounts.clear()
    }
}
