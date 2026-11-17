package com.sam.assistant.agent.planner

import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.RetryPolicy
import com.sam.assistant.agent.model.TaskStatus
import com.sam.assistant.automation.core.RiskLevel
import com.sam.assistant.automation.executor.ActionType
import java.util.UUID

class TaskDecomposer {

    fun decomposeGoal(goal: AgentGoal): List<AgentTask> {
        val tasks = mutableListOf<AgentTask>()
        val raw = goal.rawPrompt.lowercase()
        val entities = goal.entities

        val baseId = UUID.randomUUID().toString().take(6)

        // Case 1: Browser + Search or Play media
        if (raw.contains("brave") || (raw.contains("browser") && (raw.contains("search") || raw.contains("play")))) {
            val app = entities["targetApp"] ?: "brave"
            val task1Id = "task_${baseId}_verify_app"
            val task1 = AgentTask(
                taskId = task1Id,
                goalId = goal.goalId,
                description = "Verify $app is installed",
                actionType = ActionType.VERIFY_APP_INSTALLED,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("target" to app),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "PACKAGE_CHECK"
            )
            tasks.add(task1)

            if (raw.contains("search")) {
                val query = entities["query"] ?: raw.substringAfter("search for", raw.substringAfter("search", "cybersecurity news")).trim()
                val task2Id = "task_${baseId}_search_web"
                val task2 = AgentTask(
                    taskId = task2Id,
                    goalId = goal.goalId,
                    parentTaskId = task1Id,
                    description = "Search web for \"$query\" using $app",
                    actionType = ActionType.SEARCH_WEB,
                    toolName = "BrowserResearchTool",
                    inputParameters = mapOf("target" to app, "query" to query),
                    dependencies = listOf(task1Id),
                    priority = 2,
                    riskLevel = RiskLevel.LOW,
                    verificationMethod = "INTENT_ACCEPTED"
                )
                tasks.add(task2)
            } else if (raw.contains("play") || raw.contains("music") || raw.contains("chalao")) {
                val mediaQuery = entities["mediaQuery"] ?: "relaxing music"
                val task2Id = "task_${baseId}_launch_app"
                val task2 = AgentTask(
                    taskId = task2Id,
                    goalId = goal.goalId,
                    parentTaskId = task1Id,
                    description = "Launch $app",
                    actionType = ActionType.LAUNCH_APP,
                    toolName = "AndroidAppTool",
                    inputParameters = mapOf("target" to app),
                    dependencies = listOf(task1Id),
                    priority = 2,
                    riskLevel = RiskLevel.LOW,
                    verificationMethod = "PACKAGE_RESOLVE"
                )
                tasks.add(task2)

                val task3Id = "task_${baseId}_play_media"
                val task3 = AgentTask(
                    taskId = task3Id,
                    goalId = goal.goalId,
                    parentTaskId = task2Id,
                    description = "Play \"$mediaQuery\" in $app",
                    actionType = ActionType.PLAY_MEDIA,
                    toolName = "BrowserResearchTool",
                    inputParameters = mapOf("target" to app, "query" to mediaQuery),
                    dependencies = listOf(task2Id),
                    priority = 3,
                    riskLevel = RiskLevel.LOW,
                    verificationMethod = "INTENT_ACCEPTED"
                )
                tasks.add(task3)
            }
            return tasks
        }

        // Case 2: YouTube Search / Play
        if (raw.contains("youtube")) {
            val task1Id = "task_${baseId}_verify_yt"
            val task1 = AgentTask(
                taskId = task1Id,
                goalId = goal.goalId,
                description = "Verify YouTube is installed",
                actionType = ActionType.VERIFY_APP_INSTALLED,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("target" to "youtube"),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "PACKAGE_CHECK"
            )
            tasks.add(task1)

            val query = entities["query"] ?: entities["mediaQuery"] ?: raw.substringAfter("search for", raw.substringAfter("search", "trending videos")).trim()
            val task2Id = "task_${baseId}_yt_search"
            val task2 = AgentTask(
                taskId = task2Id,
                goalId = goal.goalId,
                parentTaskId = task1Id,
                description = "Search YouTube for \"$query\"",
                actionType = ActionType.SEARCH_YOUTUBE,
                toolName = "YouTubeResearchTool",
                inputParameters = mapOf("target" to "youtube", "query" to query),
                dependencies = listOf(task1Id),
                priority = 2,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "INTENT_ACCEPTED"
            )
            tasks.add(task2)
            return tasks
        }

        // Case 3: WhatsApp
        if (raw.contains("whatsapp")) {
            val task1Id = "task_${baseId}_verify_wa"
            val task1 = AgentTask(
                taskId = task1Id,
                goalId = goal.goalId,
                description = "Verify WhatsApp is installed",
                actionType = ActionType.VERIFY_APP_INSTALLED,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("target" to "whatsapp"),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "PACKAGE_CHECK"
            )
            tasks.add(task1)

            val recipient = entities["recipient"] ?: "contact"
            val message = raw.substringAfter("message", "").substringAfter("saying", "").trim().ifBlank { "Hello" }

            val task2Id = "task_${baseId}_prep_wa"
            val task2 = AgentTask(
                taskId = task2Id,
                goalId = goal.goalId,
                parentTaskId = task1Id,
                description = "Prepare WhatsApp message to $recipient",
                actionType = ActionType.PREPARE_WHATSAPP,
                toolName = "CommunicationTool",
                inputParameters = mapOf("recipient" to recipient, "message" to message),
                dependencies = listOf(task1Id),
                priority = 2,
                riskLevel = RiskLevel.MEDIUM,
                requiresApproval = true,
                verificationMethod = "INTENT_ACCEPTED"
            )
            tasks.add(task2)
            return tasks
        }

        // Case 4: Phone Calls
        if (raw.contains("call") || raw.contains("dial") || raw.contains("phone")) {
            val recipient = entities["recipient"] ?: raw.substringAfter("call").substringAfter("dial").trim()
            val taskId = "task_${baseId}_call"
            val task = AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = "Place phone call to $recipient",
                actionType = ActionType.MAKE_CALL,
                toolName = "CommunicationTool",
                inputParameters = mapOf("recipient" to recipient),
                priority = 1,
                riskLevel = RiskLevel.HIGH,
                requiresApproval = true,
                requiredPermissions = listOf("android.permission.CALL_PHONE"),
                verificationMethod = "SYSTEM_CALL_INITIATED"
            )
            tasks.add(task)
            return tasks
        }

        // Case 5: SMS
        if (raw.contains("sms") || raw.contains("send message")) {
            val recipient = entities["recipient"] ?: "contact"
            val message = raw.substringAfter("saying", "").trim().ifBlank { "Hello from SAM" }
            val taskId = "task_${baseId}_sms"
            val task = AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = "Send SMS to $recipient",
                actionType = ActionType.SEND_SMS,
                toolName = "CommunicationTool",
                inputParameters = mapOf("recipient" to recipient, "message" to message),
                priority = 1,
                riskLevel = RiskLevel.HIGH,
                requiresApproval = true,
                requiredPermissions = listOf("android.permission.SEND_SMS"),
                verificationMethod = "SMS_DISPATCHED"
            )
            tasks.add(task)
            return tasks
        }

        // Case 6: Torch / Flashlight
        if (raw.contains("torch") || raw.contains("flashlight")) {
            val turnOn = !raw.contains("off") && !raw.contains("band")
            val taskId = "task_${baseId}_torch"
            val task = AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = if (turnOn) "Turn on flashlight" else "Turn off flashlight",
                actionType = ActionType.TOGGLE_FLASHLIGHT,
                toolName = "DeviceSettingTool",
                inputParameters = mapOf("state" to if (turnOn) "on" else "off"),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "CAMERA_TORCH_STATE"
            )
            tasks.add(task)
            return tasks
        }

        // Case 7: Battery
        if (raw.contains("battery")) {
            val taskId = "task_${baseId}_battery"
            val task = AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = "Check battery status",
                actionType = ActionType.READ_BATTERY,
                toolName = "DeviceSettingTool",
                inputParameters = emptyMap(),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "BATTERY_MANAGER_READ"
            )
            tasks.add(task)
            return tasks
        }

        // Case 8: Alarm / Timer
        if (raw.contains("alarm") || raw.contains("timer")) {
            val actionType = if (raw.contains("alarm")) ActionType.SET_ALARM else ActionType.CREATE_REMINDER
            val taskId = "task_${baseId}_alarm"
            val task = AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = "Set alarm/timer",
                actionType = actionType,
                toolName = "AlarmTimerTool",
                inputParameters = mapOf("raw" to goal.rawPrompt),
                priority = 1,
                riskLevel = RiskLevel.MEDIUM,
                verificationMethod = "INTENT_ACCEPTED"
            )
            tasks.add(task)
            return tasks
        }

        // Case 9: App Launch
        if (entities.containsKey("targetApp") || raw.contains("open") || raw.contains("launch") || raw.contains("kholo")) {
            val app = entities["targetApp"] ?: raw.substringAfter("open").substringAfter("launch").substringAfter("kholo").trim().split(" ").first()
            val task1Id = "task_${baseId}_verify_app"
            val task1 = AgentTask(
                taskId = task1Id,
                goalId = goal.goalId,
                description = "Verify $app is installed",
                actionType = ActionType.VERIFY_APP_INSTALLED,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("target" to app),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "PACKAGE_CHECK"
            )
            val task2Id = "task_${baseId}_launch_app"
            val task2 = AgentTask(
                taskId = task2Id,
                goalId = goal.goalId,
                parentTaskId = task1Id,
                description = "Launch $app",
                actionType = ActionType.LAUNCH_APP,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("target" to app),
                dependencies = listOf(task1Id),
                priority = 2,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "PACKAGE_RESOLVE"
            )
            tasks.add(task1)
            tasks.add(task2)
            return tasks
        }

        // Fallback: General task
        val taskId = "task_${baseId}_exec"
        tasks.add(
            AgentTask(
                taskId = taskId,
                goalId = goal.goalId,
                description = "Execute operation: ${goal.mainObjective}",
                actionType = ActionType.CONVERSATIONAL_RESPONSE,
                toolName = "AndroidAppTool",
                inputParameters = mapOf("prompt" to goal.rawPrompt),
                priority = 1,
                riskLevel = RiskLevel.LOW,
                verificationMethod = "INTENT_ACCEPTED"
            )
        )
        return tasks
    }
}
