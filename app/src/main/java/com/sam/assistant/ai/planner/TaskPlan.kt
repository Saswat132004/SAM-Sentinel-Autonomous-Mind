package com.sam.assistant.ai.planner

import com.sam.assistant.automation.executor.ActionRequest

data class TaskStep(
    val stepIndex: Int,
    val description: String,
    val actionRequest: ActionRequest,
    val isCritical: Boolean = true
)

data class TaskPlan(
    val planId: String = java.util.UUID.randomUUID().toString(),
    val originalCommand: String,
    val intent: String,
    val targetApp: String? = null,
    val steps: List<TaskStep>,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = ""
)
