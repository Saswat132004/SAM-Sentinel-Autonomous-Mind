package com.sam.assistant.agent.tools

import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.automation.executor.ActionType

class ToolSelector(private val registry: ToolRegistry) {

    fun selectToolForTask(task: AgentTask): AgentTool? {
        // 1. Exact tool name match
        registry.getTool(task.toolName)?.let { return it }

        // 2. ActionType based matching
        return when (task.actionType) {
            ActionType.LAUNCH_APP, ActionType.VERIFY_APP_INSTALLED, ActionType.LIST_APPS ->
                registry.getTool("AndroidAppTool")

            ActionType.TOGGLE_FLASHLIGHT, ActionType.READ_BATTERY, ActionType.READ_TIME_DATE, ActionType.ADJUST_VOLUME, ActionType.OPEN_SETTINGS ->
                registry.getTool("DeviceSettingTool")

            ActionType.SET_ALARM, ActionType.CREATE_REMINDER ->
                registry.getTool("AlarmTimerTool")

            ActionType.MAKE_CALL, ActionType.SEND_SMS, ActionType.SEND_WHATSAPP, ActionType.PREPARE_WHATSAPP ->
                registry.getTool("CommunicationTool")

            ActionType.SEARCH_WEB, ActionType.OPEN_URL, ActionType.PLAY_MEDIA ->
                registry.getTool("BrowserResearchTool")

            ActionType.SEARCH_YOUTUBE ->
                registry.getTool("YouTubeResearchTool")

            ActionType.SAVE_MEMORY, ActionType.QUERY_MEMORY, ActionType.DELETE_MEMORY ->
                registry.getTool("MemoryIntelligenceTool")

            else -> registry.getTool("AndroidAppTool")
        }
    }
}
