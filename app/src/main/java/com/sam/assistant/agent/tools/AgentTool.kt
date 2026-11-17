package com.sam.assistant.agent.tools

import com.sam.assistant.automation.core.RiskLevel

interface AgentTool {
    val name: String
    val category: ToolCategory
    val description: String
    val riskLevel: RiskLevel
    val requiredPermissions: List<String>
        get() = emptyList()

    suspend fun execute(parameters: Map<String, String>, confirmed: Boolean = false): ToolExecutionResult
}
