package com.sam.assistant.agent.planner

import com.sam.assistant.agent.model.AgentGoal
import java.util.UUID

class GoalInterpreter {

    companion object {
        private val SENSITIVE_KEYWORDS = listOf(
            "call", "dial", "phone", "sms", "message", "whatsapp",
            "bhejo", "kholo aur message", "delete", "clear", "wipe",
            "shutdown", "restart"
        )

        private val APP_LAUNCH_KEYWORDS = listOf("open", "launch", "kholo", "start", "run")
        private val WEB_SEARCH_KEYWORDS = listOf("search", "google", "find", "dhundo", "lookup", "explore")
        private val MEDIA_KEYWORDS = listOf("play", "chalao", "song", "music", "video", "youtube")
        private val DEVICE_KEYWORDS = listOf("torch", "flashlight", "battery", "volume", "sound", "alarm", "timer")
    }

    /**
     * Interprets a raw user instruction into a structured AgentGoal.
     */
    fun interpretGoal(rawPrompt: String): AgentGoal {
        val trimmed = rawPrompt.trim()
        val lower = trimmed.lowercase()
        val goalId = "goal_" + UUID.randomUUID().toString().take(8)

        val isSensitive = SENSITIVE_KEYWORDS.any { lower.contains(it) }
        val entities = extractEntities(lower)
        val subtasks = identifyCandidateSubtasks(lower, entities)
        val tools = identifyRequiredTools(lower)
        val missingInfo = checkMissingInfo(lower, entities)

        val requiresApproval = isSensitive && (lower.contains("send") || lower.contains("call") || lower.contains("delete"))

        val clarification = if (missingInfo.isNotEmpty()) {
            "Please specify: " + missingInfo.joinToString(", ")
        } else {
            null
        }

        return AgentGoal(
            goalId = goalId,
            rawPrompt = trimmed,
            mainObjective = trimmed,
            requiredSubtasks = subtasks,
            entities = entities,
            requiredTools = tools,
            expectedOutputs = listOf("Task completion verification"),
            missingInfo = missingInfo,
            isSensitive = isSensitive,
            requiresApproval = requiresApproval,
            clarificationPrompt = clarification
        )
    }

    private fun extractEntities(prompt: String): Map<String, String> {
        val entities = mutableMapOf<String, String>()

        // Target application
        val apps = listOf("brave", "chrome", "youtube", "whatsapp", "spotify", "settings", "camera", "files")
        for (app in apps) {
            if (prompt.contains(app)) {
                entities["targetApp"] = app
                break
            }
        }

        // Contact name or query
        val contactMatch = Regex("(?:call|dial|phone|to|ko)\\s+([a-zA-Z0-9]+)").find(prompt)
        contactMatch?.groups?.get(1)?.value?.let {
            entities["recipient"] = it.replaceFirstChar { char -> char.uppercase() }
        }

        // Search query extraction
        if (prompt.contains("search for ") || prompt.contains("search ")) {
            val queryMatch = Regex("search(?:\\s+for)?\\s+([^,.]+)").find(prompt)
            queryMatch?.groups?.get(1)?.value?.let {
                entities["query"] = it.trim()
            }
        } else if (prompt.contains("play ")) {
            val playMatch = Regex("play\\s+([^,.]+)").find(prompt)
            playMatch?.groups?.get(1)?.value?.let {
                entities["mediaQuery"] = it.replace("using brave", "").replace("on youtube", "").trim()
            }
        }

        // Time / Minutes
        val timeMatch = Regex("(\\d+)\\s*(?:minutes|mins|minute|baje|am|pm)").find(prompt)
        timeMatch?.value?.let {
            entities["time"] = it
        }

        return entities
    }

    private fun identifyCandidateSubtasks(prompt: String, entities: Map<String, String>): List<String> {
        val tasks = mutableListOf<String>()

        if (entities.containsKey("targetApp")) {
            tasks.add("Verify ${entities["targetApp"]} installed")
            tasks.add("Launch ${entities["targetApp"]}")
        }

        if (prompt.contains("search")) {
            tasks.add("Perform search query")
        } else if (prompt.contains("play")) {
            tasks.add("Play requested media")
        }

        if (prompt.contains("whatsapp") || prompt.contains("message") || prompt.contains("sms")) {
            tasks.add("Prepare communication payload")
            tasks.add("Dispatch communication")
        }

        if (prompt.contains("battery") || prompt.contains("torch") || prompt.contains("alarm")) {
            tasks.add("Control device setting")
        }

        if (tasks.isEmpty()) {
            tasks.add("Execute requested operation")
        }

        return tasks
    }

    private fun identifyRequiredTools(prompt: String): List<String> {
        val tools = mutableListOf<String>()
        if (prompt.contains("brave") || prompt.contains("chrome") || prompt.contains("browser") || prompt.contains("search")) {
            tools.add("BrowserResearchTool")
        }
        if (prompt.contains("youtube")) {
            tools.add("YouTubeResearchTool")
        }
        if (prompt.contains("call") || prompt.contains("sms") || prompt.contains("whatsapp") || prompt.contains("message")) {
            tools.add("CommunicationTool")
        }
        if (prompt.contains("battery") || prompt.contains("torch") || prompt.contains("volume") || prompt.contains("alarm") || prompt.contains("timer")) {
            tools.add("DeviceSettingTool")
        }
        if (prompt.contains("remember") || prompt.contains("memory") || prompt.contains("recall")) {
            tools.add("MemoryIntelligenceTool")
        }
        if (tools.isEmpty()) {
            tools.add("AndroidAppTool")
        }
        return tools
    }

    private fun checkMissingInfo(prompt: String, entities: Map<String, String>): List<String> {
        val missing = mutableListOf<String>()
        if ((prompt.contains("send message") || prompt.contains("whatsapp")) && !entities.containsKey("recipient")) {
            missing.add("recipient")
        }
        if ((prompt.contains("call") || prompt.contains("dial")) && !entities.containsKey("recipient")) {
            missing.add("contact name or phone number")
        }
        return missing
    }
}
