package com.sam.assistant.agent.execution

import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentState
import com.sam.assistant.agent.model.AgentTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentStateManager {

    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _currentGoal = MutableStateFlow<AgentGoal?>(null)
    val currentGoal: StateFlow<AgentGoal?> = _currentGoal.asStateFlow()

    private val _currentTask = MutableStateFlow<AgentTask?>(null)
    val currentTask: StateFlow<AgentTask?> = _currentTask.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun updateState(newState: AgentState) {
        _state.value = newState
        addLog("Agent state transitioned to: ${newState.name}")
    }

    fun setCurrentGoal(goal: AgentGoal?) {
        _currentGoal.value = goal
    }

    fun setCurrentTask(task: AgentTask?) {
        _currentTask.value = task
        if (task != null) {
            addLog("Executing task [${task.taskId}]: ${task.description}")
        }
    }

    fun addLog(logMessage: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formatted = "[$timestamp] $logMessage"
        _logs.value = _logs.value + formatted
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
