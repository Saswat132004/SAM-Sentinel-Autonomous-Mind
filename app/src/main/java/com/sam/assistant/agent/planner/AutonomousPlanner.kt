package com.sam.assistant.agent.planner

import com.sam.assistant.agent.graph.TaskGraph
import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentTask

class AutonomousPlanner(
    val goalInterpreter: GoalInterpreter = GoalInterpreter(),
    val taskDecomposer: TaskDecomposer = TaskDecomposer()
) {

    /**
     * Translates a user instruction into an AgentGoal and a validated Directed Acyclic TaskGraph.
     */
    fun createPlan(prompt: String): Pair<AgentGoal, TaskGraph> {
        val goal = goalInterpreter.interpretGoal(prompt)
        val tasks = taskDecomposer.decomposeGoal(goal)

        val graph = TaskGraph()
        tasks.forEach { graph.addTask(it) }

        // Validate DAG structure
        if (graph.hasCycle()) {
            throw IllegalStateException("Planner generated a task graph with a cyclic dependency: $prompt")
        }

        return Pair(goal, graph)
    }
}
