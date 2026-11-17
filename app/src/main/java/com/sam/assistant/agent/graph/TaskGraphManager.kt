package com.sam.assistant.agent.graph

import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.TaskStatus

class TaskGraphManager(val graph: TaskGraph = TaskGraph()) {

    fun initializeWithTasks(tasks: List<AgentTask>) {
        graph.tasks.clear()
        tasks.forEach { graph.addTask(it) }
        updateReadyTasks()
    }

    /**
     * Finds all tasks whose dependencies are fully COMPLETED and status is currently PENDING.
     */
    fun getReadyTasks(): List<AgentTask> {
        updateReadyTasks()
        return graph.tasks.values.filter { it.status == TaskStatus.READY }
    }

    fun updateReadyTasks() {
        for (task in graph.tasks.values) {
            if (task.status == TaskStatus.PENDING) {
                val allDepsMet = task.dependencies.all { depId ->
                    val dep = graph.getTask(depId)
                    dep != null && dep.status == TaskStatus.COMPLETED
                }
                if (allDepsMet) {
                    task.status = TaskStatus.READY
                }
            }
        }
    }

    fun markTaskStatus(
        taskId: String,
        status: TaskStatus,
        evidence: String? = null,
        errorMessage: String? = null
    ) {
        val task = graph.getTask(taskId) ?: return
        task.status = status
        if (evidence != null) task.executionEvidence = evidence
        if (errorMessage != null) task.errorMessage = errorMessage

        if (status == TaskStatus.COMPLETED) {
            updateReadyTasks()
        }
    }

    fun injectTask(task: AgentTask) {
        graph.addTask(task)
        updateReadyTasks()
    }

    fun replaceTask(oldTaskId: String, newTask: AgentTask) {
        graph.tasks.remove(oldTaskId)
        graph.addTask(newTask)
        // Update any other task pointing to oldTaskId
        for (other in graph.tasks.values) {
            if (other.dependencies.contains(oldTaskId)) {
                val updatedDeps = other.dependencies.toMutableList()
                updatedDeps.remove(oldTaskId)
                updatedDeps.add(newTask.taskId)
                graph.addTask(other.copy(dependencies = updatedDeps))
            }
        }
        updateReadyTasks()
    }

    fun isFinished(): Boolean {
        if (graph.tasks.isEmpty()) return true
        return graph.tasks.values.all {
            it.status == TaskStatus.COMPLETED ||
            it.status == TaskStatus.FAILED ||
            it.status == TaskStatus.CANCELLED
        }
    }

    fun hasFailures(): Boolean {
        return graph.tasks.values.any { it.status == TaskStatus.FAILED }
    }

    fun getCompletedCount(): Int = graph.tasks.values.count { it.status == TaskStatus.COMPLETED }

    fun getTotalCount(): Int = graph.tasks.size

    fun getProgressPercentage(): Float {
        val total = getTotalCount()
        if (total == 0) return 0f
        return (getCompletedCount().toFloat() / total.toFloat()) * 100f
    }
}
