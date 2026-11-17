package com.sam.assistant.automation.executor

import android.content.Context
import com.sam.assistant.ai.planner.TaskPlan
import com.sam.assistant.automation.apps.AppLauncher
import com.sam.assistant.automation.browser.BrowserAutomationManager
import com.sam.assistant.automation.calls.CallManager
import com.sam.assistant.automation.core.*
import com.sam.assistant.automation.device.DeviceController
import com.sam.assistant.automation.documents.DocumentAutomationManager
import com.sam.assistant.automation.messages.MessageManager
import com.sam.assistant.automation.reminders.ReminderScheduler
import com.sam.assistant.memory.manager.PersonalMemoryManager
import com.sam.assistant.memory.repository.MemoryRepository

class ActionExecutor(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val appLauncher: AppLauncher,
    private val callManager: CallManager,
    private val messageManager: MessageManager,
    private val deviceController: DeviceController,
    private val reminderScheduler: ReminderScheduler,
    private val personalMemoryManager: PersonalMemoryManager
) {

    val orchestrator: TaskOrchestrator = TaskOrchestrator(
        context = context,
        validator = ActionValidator(context),
        resultVerifier = ResultVerifier(context),
        actionHistory = ActionHistoryRepository(memoryRepository),
        appLauncher = appLauncher,
        browserManager = BrowserAutomationManager(context, ResultVerifier(context)),
        callManager = callManager,
        messageManager = messageManager,
        deviceController = deviceController,
        documentManager = DocumentAutomationManager(context),
        personalMemoryManager = personalMemoryManager
    )

    /**
     * Executes a decomposed TaskPlan sequentially, verifying each step.
     */
    suspend fun executePlan(plan: TaskPlan, confirmed: Boolean = false): ExecutionResult {
        return orchestrator.executePlan(plan, confirmed)
    }

    /**
     * Executes a single ActionRequest with security validation and logging.
     */
    suspend fun execute(request: ActionRequest, confirmed: Boolean = false): ExecutionResult {
        return orchestrator.executeAction(request, confirmed)
    }
}
