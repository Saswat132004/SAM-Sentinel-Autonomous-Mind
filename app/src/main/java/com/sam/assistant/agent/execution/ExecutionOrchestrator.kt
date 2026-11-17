package com.sam.assistant.agent.execution

import android.util.Log
import com.sam.assistant.agent.graph.TaskGraphManager
import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentState
import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.GoalExecutionResult
import com.sam.assistant.agent.model.TaskStatus
import com.sam.assistant.agent.tools.ToolExecutionResult
import com.sam.assistant.agent.tools.ToolSelector
import com.sam.assistant.automation.core.RiskLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.suspendCoroutine

class ExecutionOrchestrator(
    private val graphManager: TaskGraphManager,
    private val toolSelector: ToolSelector,
    private val stateManager: AgentStateManager,
    private val approvalManager: UserApprovalManager,
    private val recoveryEngine: RecoveryEngine
) {

    companion object {
        private const val TAG = "ExecutionOrchestrator"
    }

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
        stateManager.updateState(AgentState.CANCELLED)
        stateManager.addLog("Execution cancelled by user request.")
    }

    fun reset() {
        isCancelled.set(false)
    }

    /**
     * Executes the task graph to completion or until blocked/failed.
     */
    suspend fun executeGoal(goal: AgentGoal): GoalExecutionResult {
        if (isCancelled.get()) {
            stateManager.updateState(AgentState.CANCELLED)
            return GoalExecutionResult(
                goalId = goal.goalId,
                status = AgentState.CANCELLED,
                completedTasks = 0,
                totalTasks = graphManager.getTotalCount(),
                summary = "Execution was cancelled.",
                evidenceSummary = emptyList()
            )
        }
        recoveryEngine.reset()
        stateManager.setCurrentGoal(goal)
        stateManager.updateState(AgentState.EXECUTING_GRAPH)

        val evidenceList = mutableListOf<String>()

        try {
            val orderedTasks = try {
                graphManager.graph.getTopologicalOrder()
            } catch (e: Exception) {
                stateManager.updateState(AgentState.FAILED)
                return GoalExecutionResult(
                    goalId = goal.goalId,
                    status = AgentState.FAILED,
                    completedTasks = 0,
                    totalTasks = graphManager.getTotalCount(),
                    summary = "Cycle detected in task execution graph: ${e.message}",
                    failedTaskReason = e.message
                )
            }

            for (task in orderedTasks) {
                if (isCancelled.get()) {
                    graphManager.markTaskStatus(task.taskId, TaskStatus.CANCELLED)
                    stateManager.updateState(AgentState.CANCELLED)
                    return GoalExecutionResult(
                        goalId = goal.goalId,
                        status = AgentState.CANCELLED,
                        completedTasks = graphManager.getCompletedCount(),
                        totalTasks = graphManager.getTotalCount(),
                        summary = "Execution was cancelled.",
                        evidenceSummary = evidenceList
                    )
                }

                // Check dependencies before executing
                val depsCompleted = task.dependencies.all { depId ->
                    graphManager.graph.getTask(depId)?.status == TaskStatus.COMPLETED
                }
                if (!depsCompleted) {
                    graphManager.markTaskStatus(task.taskId, TaskStatus.BLOCKED, errorMessage = "Prerequisite dependencies not met.")
                    continue
                }

                // Execute single task with recovery
                val taskSuccess = executeTaskWithRecovery(task, evidenceList)
                if (!taskSuccess) {
                    stateManager.updateState(AgentState.FAILED)
                    return GoalExecutionResult(
                        goalId = goal.goalId,
                        status = AgentState.FAILED,
                        completedTasks = graphManager.getCompletedCount(),
                        totalTasks = graphManager.getTotalCount(),
                        summary = "Task failed: ${task.description}",
                        evidenceSummary = evidenceList,
                        failedTaskReason = task.errorMessage ?: "Unknown error"
                    )
                }
            }

            stateManager.updateState(AgentState.COMPLETED)
            stateManager.setCurrentTask(null)
            return GoalExecutionResult(
                goalId = goal.goalId,
                status = AgentState.COMPLETED,
                completedTasks = graphManager.getCompletedCount(),
                totalTasks = graphManager.getTotalCount(),
                summary = "All ${graphManager.getCompletedCount()} tasks executed and verified successfully.",
                evidenceSummary = evidenceList
            )

        } catch (e: CancellationException) {
            stateManager.updateState(AgentState.CANCELLED)
            return GoalExecutionResult(
                goalId = goal.goalId,
                status = AgentState.CANCELLED,
                completedTasks = graphManager.getCompletedCount(),
                totalTasks = graphManager.getTotalCount(),
                summary = "Execution coroutine cancelled.",
                evidenceSummary = evidenceList
            )
        } catch (e: Exception) {
            stateManager.updateState(AgentState.FAILED)
            return GoalExecutionResult(
                goalId = goal.goalId,
                status = AgentState.FAILED,
                completedTasks = graphManager.getCompletedCount(),
                totalTasks = graphManager.getTotalCount(),
                summary = "Execution error: ${e.message}",
                evidenceSummary = evidenceList,
                failedTaskReason = e.message
            )
        }
    }

    private suspend fun executeTaskWithRecovery(
        task: AgentTask,
        evidenceList: MutableList<String>
    ): Boolean {
        var currentTask = task

        while (!isCancelled.get()) {
            stateManager.setCurrentTask(currentTask)
            graphManager.markTaskStatus(currentTask.taskId, TaskStatus.EXECUTING)

            // 1. Approval Gate
            var approved = false
            if (currentTask.requiresApproval || currentTask.riskLevel == RiskLevel.HIGH) {
                stateManager.updateState(AgentState.AWAITING_APPROVAL)
                graphManager.markTaskStatus(currentTask.taskId, TaskStatus.WAITING_FOR_APPROVAL)

                val prompt = "SAM Autonomous Agent requires approval to execute: ${currentTask.description}"
                approved = suspendCoroutine { cont ->
                    approvalManager.requestApproval(currentTask, prompt, cont)
                }

                if (!approved) {
                    graphManager.markTaskStatus(currentTask.taskId, TaskStatus.CANCELLED, errorMessage = "Denied by user.")
                    stateManager.addLog("Task denied by user: ${currentTask.description}")
                    return false
                }
                stateManager.updateState(AgentState.EXECUTING_GRAPH)
            }

            // 2. Tool Lookup
            val tool = toolSelector.selectToolForTask(currentTask)
            if (tool == null) {
                val errorMsg = "No tool registered to handle task: ${currentTask.toolName}"
                graphManager.markTaskStatus(currentTask.taskId, TaskStatus.FAILED, errorMessage = errorMsg)
                return false
            }

            // 3. Tool Execution
            graphManager.markTaskStatus(currentTask.taskId, TaskStatus.EXECUTING)
            val result = tool.execute(currentTask.inputParameters, confirmed = approved)

            // 4. Handle Result & Recovery
            when (result) {
                is ToolExecutionResult.Success -> {
                    graphManager.markTaskStatus(
                        currentTask.taskId,
                        TaskStatus.COMPLETED,
                        evidence = result.evidence
                    )
                    evidenceList.add("[${currentTask.taskId}] ${result.evidence}")
                    stateManager.addLog("Task [${currentTask.taskId}] succeeded: ${result.userMessage}")
                    return true
                }
                is ToolExecutionResult.RequiresApproval -> {
                    // Tool dynamically requested approval
                    stateManager.updateState(AgentState.AWAITING_APPROVAL)
                    graphManager.markTaskStatus(currentTask.taskId, TaskStatus.WAITING_FOR_APPROVAL)
                    val userConfirmed = suspendCoroutine<Boolean> { cont ->
                        approvalManager.requestApproval(currentTask, result.prompt, cont)
                    }
                    if (!userConfirmed) {
                        graphManager.markTaskStatus(currentTask.taskId, TaskStatus.CANCELLED, errorMessage = "Denied by user.")
                        return false
                    }
                    stateManager.updateState(AgentState.EXECUTING_GRAPH)
                    // Retry execution now confirmed
                    val secondResult = tool.execute(result.parameters, confirmed = true)
                    if (secondResult is ToolExecutionResult.Success) {
                        graphManager.markTaskStatus(currentTask.taskId, TaskStatus.COMPLETED, evidence = secondResult.evidence)
                        evidenceList.add("[${currentTask.taskId}] ${secondResult.evidence}")
                        return true
                    } else {
                        graphManager.markTaskStatus(currentTask.taskId, TaskStatus.FAILED, errorMessage = "Failed after approval")
                        return false
                    }
                }
                is ToolExecutionResult.PermissionRequired -> {
                    graphManager.markTaskStatus(
                        currentTask.taskId,
                        TaskStatus.BLOCKED,
                        errorMessage = "Permission required: ${result.permission}"
                    )
                    return false
                }
                is ToolExecutionResult.Failed -> {
                    stateManager.updateState(AgentState.RECOVERING)
                    stateManager.addLog("Task [${currentTask.taskId}] failed: ${result.reason}. Consulting RecoveryEngine.")

                    val recovery = recoveryEngine.determineRecovery(currentTask, result, graphManager)
                    when (recovery) {
                        is RecoveryAction.Retry -> {
                            stateManager.addLog("Retrying task [${currentTask.taskId}] in ${recovery.delayMs}ms...")
                            delay(recovery.delayMs)
                            // Loop continues to retry currentTask
                        }
                        is RecoveryAction.SubstituteTool -> {
                            stateManager.addLog("Substituting task with alternative: ${recovery.alternativeTask.description}")
                            graphManager.replaceTask(currentTask.taskId, recovery.alternativeTask)
                            currentTask = recovery.alternativeTask
                            // Loop continues with substituted task
                        }
                        is RecoveryAction.GracefulDegrade -> {
                            stateManager.addLog("Graceful degrade: ${recovery.warningMessage}")
                            graphManager.markTaskStatus(currentTask.taskId, TaskStatus.COMPLETED, evidence = recovery.warningMessage)
                            return true
                        }
                        is RecoveryAction.Abort -> {
                            graphManager.markTaskStatus(currentTask.taskId, TaskStatus.FAILED, errorMessage = recovery.reason)
                            stateManager.addLog("Execution aborted: ${recovery.reason}")
                            return false
                        }
                    }
                }
            }
        }
        return false
    }
}
