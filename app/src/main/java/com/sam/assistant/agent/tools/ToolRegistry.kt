package com.sam.assistant.agent.tools

import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {

    private val toolsByName = ConcurrentHashMap<String, AgentTool>()

    fun registerTool(tool: AgentTool) {
        toolsByName[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? = toolsByName[name]

    fun getAllTools(): List<AgentTool> = toolsByName.values.toList()

    fun getToolsByCategory(category: ToolCategory): List<AgentTool> {
        return toolsByName.values.filter { it.category == category }
    }

    fun hasTool(name: String): Boolean = toolsByName.containsKey(name)
}
