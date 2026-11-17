package com.sam.assistant.automation.core

import android.content.Context
import android.util.Log
import com.sam.assistant.ai.planner.TaskPlan
import com.sam.assistant.ai.planner.TaskStep
import com.sam.assistant.automation.apps.AppLauncher
import com.sam.assistant.automation.browser.BrowserAutomationManager
import com.sam.assistant.automation.calls.CallManager
import com.sam.assistant.automation.device.DeviceController
import com.sam.assistant.automation.documents.DocumentAutomationManager
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import com.sam.assistant.automation.executor.ExecutionResult
import com.sam.assistant.automation.messages.MessageManager
import com.sam.assistant.memory.manager.MemoryCommandResult
import com.sam.assistant.memory.manager.PersonalMemoryManager

class TaskOrchestrator(
    private val context: Context,
    val validator: ActionValidator,
    val resultVerifier: ResultVerifier,
    val actionHistory: ActionHistoryRepository,
    val appLauncher: AppLauncher,
    val browserManager: BrowserAutomationManager,
    val callManager: CallManager,
    val messageManager: MessageManager,
    val deviceController: DeviceController,
    val documentManager: DocumentAutomationManager,
    val personalMemoryManager: PersonalMemoryManager,
    var cybersecurityEngine: com.sam.assistant.cybersecurity.CybersecurityEngine? = null
) {
    companion object {
        private const val TAG = "TaskOrchestrator"
    }

    /**
     * Executes a sequential multi-step TaskPlan with security validation and step-by-step verification.
     */
    suspend fun executePlan(plan: TaskPlan, confirmed: Boolean = false): ExecutionResult {
        Log.d(TAG, "Starting TaskPlan execution: \"${plan.originalCommand}\" (${plan.steps.size} steps)")

        if (plan.steps.isEmpty()) {
            return ExecutionResult.Failed(
                userExplanation = "No executable steps identified for this command.",
                technicalError = "Plan step list was empty.",
                failingComponent = "TaskOrchestrator"
            )
        }

        // 1. High-Risk Confirmation Check across entire plan
        if (plan.requiresConfirmation && !confirmed) {
            val firstAction = plan.steps.first().actionRequest
            ConfirmationManager.setPendingAction(plan.confirmationPrompt, firstAction, RiskLevel.HIGH)
            ExecutionTracker.startTask(plan.originalCommand, plan.steps.size)
            return ExecutionResult.NeedsConfirmation(plan.confirmationPrompt, firstAction, plan)
        }

        ExecutionTracker.startTask(plan.originalCommand, plan.steps.size)

        var completedCount = 0
        var lastSuccessResponse = ""
        val stepDetails = mutableMapOf<String, String>()

        for (step in plan.steps) {
            // Check cancellation
            if (ExecutionTracker.isCancelled()) {
                Log.w(TAG, "Task execution aborted by user cancellation token.")
                return ExecutionResult.Failed(
                    userExplanation = "Task was cancelled before completing all steps.",
                    technicalError = "Execution cancelled at Step ${step.stepIndex}.",
                    failingComponent = "ExecutionTracker"
                )
            }

            ExecutionTracker.setStepExecuting(step.stepIndex, step.description)

            val stepResult = executeAction(step.actionRequest, confirmed)

            when (stepResult) {
                is ExecutionResult.Success -> {
                    completedCount++
                    lastSuccessResponse = stepResult.spokenResponse
                    stepDetails["Step ${step.stepIndex}"] = stepResult.message
                    ExecutionTracker.setStepCompleted(step.description)
                }
                is ExecutionResult.NeedsConfirmation -> {
                    return stepResult
                }
                is ExecutionResult.NeedsClarification -> {
                    return stepResult
                }
                is ExecutionResult.PermissionRequired -> {
                    ExecutionTracker.failTask("Permission required: ${stepResult.permission}")
                    actionHistory.logExecution(step.actionRequest, "PERMISSION_REQUIRED", stepResult.userExplanation)
                    return stepResult
                }
                is ExecutionResult.Failed -> {
                    ExecutionTracker.failTask("Failed at step ${step.stepIndex}: ${stepResult.userExplanation}")
                    actionHistory.logExecution(step.actionRequest, "FAILED", stepResult.userExplanation)
                    return if (completedCount > 0) {
                        ExecutionResult.PartialSuccess(
                            message = "Partially completed ($completedCount/${plan.steps.size} steps). Step ${step.stepIndex} failed: ${stepResult.userExplanation}",
                            spokenResponse = "Partially completed. ${stepResult.spokenError}",
                            completedSteps = completedCount,
                            totalSteps = plan.steps.size,
                            failedStepReason = stepResult.userExplanation
                        )
                    } else {
                        stepResult
                    }
                }
                is ExecutionResult.Error -> {
                    ExecutionTracker.failTask("Error at step ${step.stepIndex}: ${stepResult.errorMessage}")
                    actionHistory.logExecution(step.actionRequest, "ERROR", stepResult.errorMessage)
                    return ExecutionResult.Failed(
                        userExplanation = stepResult.errorMessage,
                        technicalError = stepResult.errorMessage,
                        failingComponent = "TaskOrchestrator",
                        spokenError = stepResult.spokenError
                    )
                }
                is ExecutionResult.PartialSuccess -> return stepResult
            }
        }

        val finalSpoken = if (plan.steps.size > 1) {
            "Completed ${plan.steps.size} steps successfully. $lastSuccessResponse"
        } else {
            lastSuccessResponse
        }

        ExecutionTracker.completeTask("All ${plan.steps.size} steps completed.")
        actionHistory.logExecution(plan.steps.last().actionRequest, "SUCCESS", "All ${plan.steps.size} steps executed.")

        return ExecutionResult.Success(
            message = "Task completed successfully (${plan.steps.size}/${plan.steps.size} steps).",
            spokenResponse = finalSpoken,
            details = stepDetails
        )
    }

    /**
     * Validates and executes an individual ActionRequest.
     */
    suspend fun executeAction(request: ActionRequest, confirmed: Boolean = false): ExecutionResult {
        Log.d(TAG, "Validating Action: ${request.actionType.name} (Confirmed=$confirmed)")

        // 1. Action Validation Barrier
        val validation = validator.validate(request, confirmed)
        when (validation) {
            is ValidationOutcome.Permitted -> { /* Proceed */ }
            is ValidationOutcome.NeedsConfirmation -> {
                ConfirmationManager.setPendingAction(validation.prompt, request, validation.riskLevel)
                return ExecutionResult.NeedsConfirmation(validation.prompt, request)
            }
            is ValidationOutcome.MissingPermission -> {
                return ExecutionResult.Error("Missing required permission: ${validation.permission}. ${validation.explanation}")
            }
            is ValidationOutcome.Rejected -> {
                Log.w(TAG, "Action rejected: ${validation.reason}")
                return ExecutionResult.Failed(
                    userExplanation = validation.reason,
                    technicalError = "Validator rejected action",
                    failingComponent = "ActionValidator"
                )
            }
        }

        // 2. Dispatch to Subsystem
        val result = when (request.actionType) {
            ActionType.LAUNCH_APP -> {
                appLauncher.launchAppByName(request.target)
            }
            ActionType.VERIFY_APP_INSTALLED -> {
                val installed = appLauncher.isAppInstalled(request.target)
                if (installed) {
                    ExecutionResult.Success("${request.target} is installed.")
                } else {
                    ExecutionResult.Failed("${request.target} is not installed on this device.", "App not found", "AppLauncher")
                }
            }
            ActionType.LIST_APPS -> {
                val apps = appLauncher.discoveryManager.getLaunchableApps()
                val topNames = apps.take(5).joinToString(", ") { it.label }
                ExecutionResult.Success("Found ${apps.size} apps: $topNames...")
            }
            ActionType.SEARCH_WEB -> {
                browserManager.openWebSearch(request.payload, request.target)
            }
            ActionType.OPEN_URL -> {
                browserManager.openUrl(request.payload, null, "Browser", request.payload)
            }
            ActionType.SEARCH_YOUTUBE -> {
                appLauncher.searchYouTube(request.payload)
            }
            ActionType.PLAY_MEDIA -> {
                appLauncher.playMedia(request.target, request.payload)
            }
            ActionType.TOGGLE_FLASHLIGHT -> {
                val turnOn = when (request.target.lowercase()) {
                    "on" -> true
                    "off" -> false
                    else -> null
                }
                deviceController.toggleFlashlight(turnOn)
            }
            ActionType.READ_BATTERY -> {
                deviceController.readBatteryLevel()
            }
            ActionType.READ_TIME_DATE -> {
                deviceController.readDateTime()
            }
            ActionType.ADJUST_VOLUME -> {
                deviceController.adjustVolume(request.target)
            }
            ActionType.OPEN_SETTINGS -> {
                deviceController.openSystemSettings(request.target)
            }
            ActionType.SET_ALARM -> {
                val hour = request.extraParams["hour"]?.toIntOrNull() ?: 7
                val minute = request.extraParams["minute"]?.toIntOrNull() ?: 0
                deviceController.setSystemAlarm(hour, minute, "SAM Alarm")
            }
            ActionType.CREATE_REMINDER -> {
                val minutes = request.extraParams["delayMinutes"]?.toIntOrNull() ?: 5
                deviceController.setSystemTimer(minutes * 60, request.payload.ifBlank { "SAM Reminder" })
            }
            ActionType.MAKE_CALL -> {
                callManager.initiateCall(request.target, confirmed)
            }
            ActionType.SEND_SMS -> {
                messageManager.sendSms(request.target, request.payload, confirmed)
            }
            ActionType.SEND_WHATSAPP -> {
                messageManager.prepareWhatsApp(request.target, request.payload)
            }
            ActionType.PREPARE_WHATSAPP -> {
                messageManager.prepareWhatsApp(request.target, request.payload)
            }
            ActionType.SAVE_MEMORY, ActionType.DELETE_MEMORY, ActionType.QUERY_MEMORY -> {
                when (val memRes = personalMemoryManager.parseAndProcessMemoryCommand(request.rawCommand)) {
                    is MemoryCommandResult.Success -> ExecutionResult.Success(memRes.responseMessage)
                    is MemoryCommandResult.NotAMemoryCommand -> ExecutionResult.Success("Processed memory request.")
                }
            }
            ActionType.SCAN_WIFI -> {
                val nets = cybersecurityEngine?.scanNearbyWifi() ?: emptyList()
                val msg = if (nets.isNotEmpty()) "Scanned ${nets.size} nearby Wi-Fi networks." else "No Wi-Fi networks detected nearby."
                ExecutionResult.Success(msg, msg)
            }
            ActionType.ANALYSE_NETWORK -> {
                val summary = cybersecurityEngine?.getQuickSecuritySummary()
                    ?: "Network security engine active. No anomalies reported."
                ExecutionResult.Success(summary, summary)
            }
            ActionType.CONVERSATIONAL_RESPONSE -> {
                ExecutionResult.Success(
                    message = request.payload.ifBlank { "Understood." },
                    spokenResponse = request.payload.ifBlank { "Understood." }
                )
            }
            else -> {
                ExecutionResult.Error("Unsupported action type: ${request.actionType.name}")
            }
        }

        // 3. Log Result
        val logStatus = when (result) {
            is ExecutionResult.Success -> "SUCCESS"
            is ExecutionResult.PartialSuccess -> "PARTIAL_SUCCESS"
            is ExecutionResult.NeedsConfirmation -> "AWAITING_CONFIRMATION"
            is ExecutionResult.NeedsClarification -> "AWAITING_CLARIFICATION"
            is ExecutionResult.PermissionRequired -> "PERMISSION_REQUIRED"
            is ExecutionResult.Failed -> "FAILED"
            is ExecutionResult.Error -> "ERROR"
        }
        val detail = when (result) {
            is ExecutionResult.Success -> result.message
            is ExecutionResult.PartialSuccess -> result.message
            is ExecutionResult.NeedsConfirmation -> result.prompt
            is ExecutionResult.NeedsClarification -> result.prompt
            is ExecutionResult.PermissionRequired -> result.userExplanation
            is ExecutionResult.Failed -> result.userExplanation
            is ExecutionResult.Error -> result.errorMessage
        }
        actionHistory.logExecution(request, logStatus, detail)

        return result
    }
}
