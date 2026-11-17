package com.sam.assistant.agent.graph

import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.TaskStatus

class TaskGraph(val tasks: MutableMap<String, AgentTask> = mutableMapOf()) {

    fun addTask(task: AgentTask) {
        tasks[task.taskId] = task
    }

    fun getTask(taskId: String): AgentTask? = tasks[taskId]

    fun getAllTasks(): List<AgentTask> = tasks.values.toList()

    /**
     * Checks if the graph contains any cycle using Depth First Search (DFS).
     * Returns true if a cycle is detected, false if it is a valid DAG.
     */
    fun hasCycle(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        for (taskId in tasks.keys) {
            if (isCyclicUtil(taskId, visited, recursionStack)) {
                return true
            }
        }
        return false
    }

    private fun isCyclicUtil(
        taskId: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>
    ): Boolean {
        if (recursionStack.contains(taskId)) return true
        if (visited.contains(taskId)) return false

        visited.add(taskId)
        recursionStack.add(taskId)

        val task = tasks[taskId]
        if (task != null) {
            for (depId in task.dependencies) {
                if (isCyclicUtil(depId, visited, recursionStack)) {
                    return true
                }
            }
        }

        recursionStack.remove(taskId)
        return false
    }

    /**
     * Returns a topological sort of the tasks.
     * Prerequisite tasks appear before dependent tasks.
     * Throws IllegalStateException if graph contains a cycle.
     */
    fun getTopologicalOrder(): List<AgentTask> {
        if (hasCycle()) {
            throw IllegalStateException("Cannot sort cyclically dependent tasks in TaskGraph.")
        }

        // Kahn's Algorithm / In-degree count
        val inDegree = mutableMapOf<String, Int>()
        tasks.keys.forEach { inDegree[it] = 0 }

        for (task in tasks.values) {
            for (dep in task.dependencies) {
                // Dependency must execute before task, so task depends on dep
                // For Kahn's: if dep -> task, inDegree[task] is the count of its dependencies!
                // Let's count in-degree as number of dependencies
            }
            inDegree[task.taskId] = task.dependencies.count { tasks.containsKey(it) }
        }

        val queue = ArrayDeque<String>()
        for ((taskId, degree) in inDegree) {
            if (degree == 0) {
                queue.add(taskId)
            }
        }

        val result = mutableListOf<AgentTask>()
        val currentDegree = inDegree.toMutableMap()

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            tasks[u]?.let { result.add(it) }

            // Find all tasks that have 'u' as a dependency
            for (task in tasks.values) {
                if (task.dependencies.contains(u)) {
                    val deg = (currentDegree[task.taskId] ?: 1) - 1
                    currentDegree[task.taskId] = deg
                    if (deg == 0) {
                        queue.add(task.taskId)
                    }
                }
            }
        }

        return result
    }
}
