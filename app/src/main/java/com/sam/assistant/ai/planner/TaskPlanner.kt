package com.sam.assistant.ai.planner

import com.sam.assistant.ai.intent.FastCommandEngine
import com.sam.assistant.ai.intent.ParsedIntent
import com.sam.assistant.ai.intent.SessionContextManager
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType

class TaskPlanner(private val fastCommandEngine: FastCommandEngine) {

    /**
     * Legacy support: returns flat list of ActionRequests.
     */
    fun planTasks(input: String): List<ActionRequest> {
        val plan = createPlan(input)
        return plan.steps.map { it.actionRequest }
    }

    /**
     * Structured task decomposition.
     * Decomposes user request into sequential, verifiable TaskSteps.
     */
    fun createPlan(input: String): TaskPlan {
        val trimmed = input.trim()

        // 1. Check for Active Clarification Selection (e.g. user answered "Brave" to "Should I use YouTube, Spotify or Brave?")
        val pendingClarification = SessionContextManager.getPendingClarification()
        if (pendingClarification != null) {
            val choice = SessionContextManager.resolveOption(trimmed, pendingClarification.options)
            if (choice != null) {
                SessionContextManager.clearClarification()
                SessionContextManager.setPreferredMusicSource(choice.lowercase())
                return decomposePlayMedia(originalCommand = trimmed, requestedApp = choice.lowercase())
            }
        }

        // 2. Check for Active Confirmation (Affirmative or Negative)
        val pendingConfirmation = SessionContextManager.getPendingConfirmation()
        if (pendingConfirmation != null) {
            if (SessionContextManager.isAffirmative(trimmed)) {
                SessionContextManager.clearConfirmation()
                // Execute the confirmed action
                val confirmedRequest = pendingConfirmation.request.copy(requiresConfirmation = false)
                return TaskPlan(
                    originalCommand = trimmed,
                    intent = "CONFIRMED_EXECUTION",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Execute confirmed action: ${confirmedRequest.actionType.name}",
                            actionRequest = confirmedRequest
                        )
                    )
                )
            } else if (SessionContextManager.isNegative(trimmed)) {
                SessionContextManager.clearConfirmation()
                return TaskPlan(
                    originalCommand = trimmed,
                    intent = "CANCEL_EXECUTION",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Cancel pending action",
                            actionRequest = ActionRequest(
                                actionType = ActionType.CANCEL_ACTION,
                                payload = "Action cancelled.",
                                rawCommand = trimmed
                            )
                        )
                    )
                )
            }
        }

        // 3. Fast Intent Parsing: Specialized domain intents take precedence
        val parsedIntent = fastCommandEngine.parseIntent(trimmed)
        if (parsedIntent != null) {
            val specializedTypes = setOf(
                ActionType.SEARCH_WEB,
                ActionType.SEARCH_YOUTUBE,
                ActionType.PLAY_MEDIA,
                ActionType.PREPARE_WHATSAPP,
                ActionType.SEND_WHATSAPP,
                ActionType.SEND_SMS,
                ActionType.MAKE_CALL,
                ActionType.ASK_CLARIFICATION,
                ActionType.CONFIRM_ACTION,
                ActionType.CANCEL_ACTION
            )
            if (specializedTypes.contains(parsedIntent.actionType)) {
                return decomposeIntent(trimmed, parsedIntent)
            }
        }

        // 4. Check for compound multi-step connectors ("and then", "then", "aur", "and")
        val splitRegex = Regex("(?i)\\s+(?:and then|then|aur fir|aur phir|aur|and)\\s+")
        val subCommands = trimmed.split(splitRegex)
        if (subCommands.size > 1) {
            val plannedSteps = mutableListOf<TaskStep>()
            for ((index, sub) in subCommands.withIndex()) {
                val subParsed = fastCommandEngine.parseIntent(sub)
                if (subParsed != null) {
                    plannedSteps.add(
                        TaskStep(
                            stepIndex = index + 1,
                            description = "Step ${index + 1}: ${subParsed.actionType.name}",
                            actionRequest = subParsed.toActionRequest()
                        )
                    )
                }
            }
            if (plannedSteps.size >= 2) {
                return TaskPlan(
                    originalCommand = trimmed,
                    intent = "MULTI_STEP_COMPOUND",
                    steps = plannedSteps
                )
            }
        }

        // 5. Standard single-step intents (flashlight, battery, time, app launch, etc.)
        if (parsedIntent != null) {
            return decomposeIntent(trimmed, parsedIntent)
        }

        // 6. Fallback unknown intent
        return TaskPlan(
            originalCommand = trimmed,
            intent = "UNKNOWN",
            steps = listOf(
                TaskStep(
                    stepIndex = 1,
                    description = "Process unknown command via local intelligence",
                    actionRequest = ActionRequest(
                        actionType = ActionType.UNKNOWN,
                        rawCommand = trimmed
                    )
                )
            )
        )
    }

    private fun decomposeIntent(originalCommand: String, intent: ParsedIntent): TaskPlan {
        return when (intent.actionType) {
            ActionType.PLAY_MEDIA -> {
                decomposePlayMedia(originalCommand, intent.target)
            }
            ActionType.SEARCH_WEB -> {
                decomposeSearchWeb(originalCommand, intent.target, intent.payload)
            }
            ActionType.SEARCH_YOUTUBE -> {
                decomposeSearchYouTube(originalCommand, intent.payload)
            }
            ActionType.PREPARE_WHATSAPP -> {
                decomposePrepareWhatsApp(originalCommand, intent.target)
            }
            ActionType.MAKE_CALL -> {
                decomposeMakeCall(originalCommand, intent.target)
            }
            ActionType.ASK_CLARIFICATION -> {
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = "CLARIFICATION_REQUIRED",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Request clarification for ambiguous command",
                            actionRequest = intent.toActionRequest()
                        )
                    )
                )
            }
            ActionType.CONFIRM_ACTION, ActionType.CANCEL_ACTION -> {
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = intent.actionType.name,
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = intent.actionType.name,
                            actionRequest = intent.toActionRequest()
                        )
                    )
                )
            }
            else -> {
                // Single step standard plan
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = intent.actionType.name,
                    targetApp = intent.target.ifEmpty { null },
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Execute ${intent.actionType.name}",
                            actionRequest = intent.toActionRequest()
                        )
                    ),
                    requiresConfirmation = intent.requiresConfirmation,
                    confirmationPrompt = intent.confirmationPrompt
                )
            }
        }
    }

    private fun decomposePlayMedia(originalCommand: String, requestedApp: String): TaskPlan {
        val app = requestedApp.lowercase().ifBlank { "youtube" }

        return when {
            app.contains("brave") -> {
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = "PLAY_MEDIA",
                    targetApp = "brave",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Verify Brave Browser is installed",
                            actionRequest = ActionRequest(
                                actionType = ActionType.VERIFY_APP_INSTALLED,
                                target = "brave",
                                rawCommand = originalCommand
                            )
                        ),
                        TaskStep(
                            stepIndex = 2,
                            description = "Launch Brave Browser with music destination",
                            actionRequest = ActionRequest(
                                actionType = ActionType.PLAY_MEDIA,
                                target = "brave",
                                payload = "https://m.youtube.com",
                                rawCommand = originalCommand
                            )
                        )
                    )
                )
            }
            app.contains("spotify") -> {
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = "PLAY_MEDIA",
                    targetApp = "spotify",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Verify Spotify is installed",
                            actionRequest = ActionRequest(
                                actionType = ActionType.VERIFY_APP_INSTALLED,
                                target = "spotify",
                                rawCommand = originalCommand
                            )
                        ),
                        TaskStep(
                            stepIndex = 2,
                            description = "Launch Spotify media playback",
                            actionRequest = ActionRequest(
                                actionType = ActionType.PLAY_MEDIA,
                                target = "spotify",
                                rawCommand = originalCommand
                            )
                        )
                    )
                )
            }
            else -> {
                // YouTube or default
                TaskPlan(
                    originalCommand = originalCommand,
                    intent = "PLAY_MEDIA",
                    targetApp = "youtube",
                    steps = listOf(
                        TaskStep(
                            stepIndex = 1,
                            description = "Launch media player in $app",
                            actionRequest = ActionRequest(
                                actionType = ActionType.PLAY_MEDIA,
                                target = app,
                                rawCommand = originalCommand
                            )
                        )
                    )
                )
            }
        }
    }

    private fun decomposeSearchWeb(originalCommand: String, browser: String, query: String): TaskPlan {
        val isWeatherSearch = query.equals("weather", ignoreCase = true) || query.contains("the weather")
        val steps = mutableListOf<TaskStep>()

        // Step 1: Verify browser installed
        steps.add(
            TaskStep(
                stepIndex = 1,
                description = "Verify $browser is installed",
                actionRequest = ActionRequest(
                    actionType = ActionType.VERIFY_APP_INSTALLED,
                    target = browser,
                    rawCommand = originalCommand
                )
            )
        )

        // Step 2: Execute search
        steps.add(
            TaskStep(
                stepIndex = 2,
                description = "Open $browser and search for \"$query\"",
                actionRequest = ActionRequest(
                    actionType = ActionType.SEARCH_WEB,
                    target = browser,
                    payload = query,
                    rawCommand = originalCommand
                )
            )
        )

        // Step 3: If user requested to read weather result:
        if (isWeatherSearch && (originalCommand.contains("read the result") || originalCommand.contains("read it"))) {
            steps.add(
                TaskStep(
                    stepIndex = 3,
                    description = "Read weather status",
                    actionRequest = ActionRequest(
                        actionType = ActionType.READ_WEB_SUMMARY,
                        target = "weather",
                        payload = query,
                        rawCommand = originalCommand
                    )
                )
            )
        }

        return TaskPlan(
            originalCommand = originalCommand,
            intent = "SEARCH_WEB",
            targetApp = browser,
            steps = steps
        )
    }

    private fun decomposeSearchYouTube(originalCommand: String, query: String): TaskPlan {
        return TaskPlan(
            originalCommand = originalCommand,
            intent = "SEARCH_YOUTUBE",
            targetApp = "youtube",
            steps = listOf(
                TaskStep(
                    stepIndex = 1,
                    description = "Search YouTube for \"$query\"",
                    actionRequest = ActionRequest(
                        actionType = ActionType.SEARCH_YOUTUBE,
                        target = "youtube",
                        payload = query,
                        rawCommand = originalCommand
                    )
                )
            )
        )
    }

    private fun decomposePrepareWhatsApp(originalCommand: String, contact: String): TaskPlan {
        return TaskPlan(
            originalCommand = originalCommand,
            intent = "PREPARE_WHATSAPP",
            targetApp = "whatsapp",
            steps = listOf(
                TaskStep(
                    stepIndex = 1,
                    description = "Verify WhatsApp is installed",
                    actionRequest = ActionRequest(
                        actionType = ActionType.VERIFY_APP_INSTALLED,
                        target = "whatsapp",
                        rawCommand = originalCommand
                    )
                ),
                TaskStep(
                    stepIndex = 2,
                    description = "Open WhatsApp conversation with $contact",
                    actionRequest = ActionRequest(
                        actionType = ActionType.PREPARE_WHATSAPP,
                        target = contact,
                        payload = "",
                        rawCommand = originalCommand
                    )
                )
            )
        )
    }

    private fun decomposeMakeCall(originalCommand: String, contact: String): TaskPlan {
        val displayName = contact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val prompt = "Are you sure you want to call $displayName?"
        return TaskPlan(
            originalCommand = originalCommand,
            intent = "MAKE_CALL",
            requiresConfirmation = true,
            confirmationPrompt = prompt,
            steps = listOf(
                TaskStep(
                    stepIndex = 1,
                    description = "Place phone call to $displayName",
                    actionRequest = ActionRequest(
                        actionType = ActionType.MAKE_CALL,
                        target = contact,
                        rawCommand = originalCommand,
                        requiresConfirmation = true,
                        confirmationPrompt = prompt
                    )
                )
            )
        )
    }
}
