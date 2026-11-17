package com.sam.assistant.agent.memory

import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentTask

class AgentMemory(private val dao: AgentTaskDao) {

    suspend fun persistGoalTasks(goal: AgentGoal, tasks: List<AgentTask>) {
        val entities = tasks.map { task ->
            AgentTaskEntity(
                goalId = goal.goalId,
                taskId = task.taskId,
                description = task.description,
                toolName = task.toolName,
                status = task.status.name,
                riskLevel = task.riskLevel.name,
                dependencies = task.dependencies.joinToString(","),
                executionEvidence = task.executionEvidence,
                errorMessage = task.errorMessage,
                timestamp = task.timestamp
            )
        }
        dao.insertTasks(entities)
    }

    suspend fun updateTaskStatus(task: AgentTask) {
        val entities = dao.getTasksForGoal(task.goalId)
        val existing = entities.find { it.taskId == task.taskId }
        if (existing != null) {
            val updated = existing.copy(
                status = task.status.name,
                executionEvidence = task.executionEvidence,
                errorMessage = task.errorMessage
            )
            dao.updateTask(updated)
        }
    }

    suspend fun getTaskHistoryForGoal(goalId: String): List<AgentTaskEntity> {
        return dao.getTasksForGoal(goalId)
    }

    suspend fun getAllTaskHistory(): List<AgentTaskEntity> {
        return dao.getAllTasks()
    }

    suspend fun deleteGoalHistory(goalId: String) {
        dao.deleteTasksForGoal(goalId)
    }

    suspend fun clearHistory() {
        dao.clearAllTasks()
    }

    fun formatTasksForExport(tasks: List<AgentTaskEntity>): String {
        val sb = StringBuilder()
        sb.append("=== SAM AUTONOMOUS AGENT TASK EXECUTION LOG ===\n\n")
        tasks.forEach {
            sb.append("Task ID: ${it.taskId} [Goal: ${it.goalId}]\n")
            sb.append("Description: ${it.description}\n")
            sb.append("Tool: ${it.toolName} | Risk: ${it.riskLevel}\n")
            sb.append("Status: ${it.status}\n")
            sb.append("Dependencies: ${if (it.dependencies.isBlank()) "None" else it.dependencies}\n")
            if (it.executionEvidence != null) {
                sb.append("Evidence: ${it.executionEvidence}\n")
            }
            if (it.errorMessage != null) {
                sb.append("Error: ${it.errorMessage}\n")
            }
            sb.append("Timestamp: ${java.util.Date(it.timestamp)}\n")
            sb.append("--------------------------------------------------\n")
        }
        return sb.toString()
    }
}
