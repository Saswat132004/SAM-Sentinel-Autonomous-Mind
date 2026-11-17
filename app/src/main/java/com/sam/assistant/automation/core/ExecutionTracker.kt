package com.sam.assistant.automation.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskExecutionStatus {
    IDLE,
    PLANNING,
    AWAITING_CONFIRMATION,
    EXECUTING_STEP,
    VERIFYING,
    STEP_COMPLETED,
    TASK_COMPLETED,
    FAILED,
    CANCELLED
}

data class ExecutionSnapshot(
    val status: TaskExecutionStatus = TaskExecutionStatus.IDLE,
    val taskDescription: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val completedSteps: List<String> = emptyList(),
    val currentStepDescription: String = "",
    val errorMessage: String? = null,
    val isCancelled: Boolean = false,
    val startTimeMs: Long = System.currentTimeMillis()
)

object ExecutionTracker {

    private const val TAG = "ExecutionTracker"

    private val _snapshot = MutableStateFlow(ExecutionSnapshot())
    val snapshot: StateFlow<ExecutionSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var isCancelledFlag = false

    fun startTask(description: String, totalSteps: Int) {
        isCancelledFlag = false
        _snapshot.value = ExecutionSnapshot(
            status = TaskExecutionStatus.PLANNING,
            taskDescription = description,
            currentStepIndex = 0,
            totalSteps = totalSteps,
            startTimeMs = System.currentTimeMillis()
        )
        Log.d(TAG, "Task execution started: \"$description\" ($totalSteps steps)")
    }

    fun setStepExecuting(stepIndex: Int, stepDescription: String) {
        if (isCancelledFlag) return
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.EXECUTING_STEP,
            currentStepIndex = stepIndex,
            currentStepDescription = stepDescription
        )
        Log.d(TAG, "Executing Step $stepIndex/${_snapshot.value.totalSteps}: $stepDescription")
    }

    fun setStepVerifying(stepIndex: Int) {
        if (isCancelledFlag) return
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.VERIFYING
        )
    }

    fun setStepCompleted(stepDescription: String) {
        if (isCancelledFlag) return
        val currentList = _snapshot.value.completedSteps.toMutableList()
        currentList.add(stepDescription)
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.STEP_COMPLETED,
            completedSteps = currentList
        )
        Log.d(TAG, "Step completed: $stepDescription (${currentList.size}/${_snapshot.value.totalSteps})")
    }

    fun completeTask(summary: String) {
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.TASK_COMPLETED
        )
        Log.d(TAG, "Task completed successfully: $summary")
    }

    fun failTask(reason: String) {
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.FAILED,
            errorMessage = reason
        )
        Log.e(TAG, "Task execution failed: $reason")
    }

    fun cancelTask() {
        isCancelledFlag = true
        _snapshot.value = _snapshot.value.copy(
            status = TaskExecutionStatus.CANCELLED,
            isCancelled = true,
            errorMessage = "Task cancelled by user"
        )
        Log.w(TAG, "Task execution cancelled.")
    }

    fun isCancelled(): Boolean = isCancelledFlag

    fun reset() {
        isCancelledFlag = false
        _snapshot.value = ExecutionSnapshot()
    }
}
