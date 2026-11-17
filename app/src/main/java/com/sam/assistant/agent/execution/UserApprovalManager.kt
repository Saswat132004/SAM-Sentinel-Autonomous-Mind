package com.sam.assistant.agent.execution

import com.sam.assistant.agent.model.AgentTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class UserApprovalManager {

    data class ApprovalRequest(
        val task: AgentTask,
        val prompt: String,
        val continuation: Continuation<Boolean>? = null
    )

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    fun requestApproval(task: AgentTask, prompt: String, continuation: Continuation<Boolean>) {
        _pendingApproval.value = ApprovalRequest(task, prompt, continuation)
    }

    fun approve(): Boolean {
        val current = _pendingApproval.value ?: return false
        _pendingApproval.value = null
        current.continuation?.resume(true)
        return true
    }

    fun reject(): Boolean {
        val current = _pendingApproval.value ?: return false
        _pendingApproval.value = null
        current.continuation?.resume(false)
        return true
    }

    fun hasPending(): Boolean = _pendingApproval.value != null
}
