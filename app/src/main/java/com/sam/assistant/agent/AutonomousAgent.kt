package com.sam.assistant.agent

import android.content.Context
import com.sam.assistant.agent.execution.AgentStateManager
import com.sam.assistant.agent.execution.ExecutionOrchestrator
import com.sam.assistant.agent.execution.RecoveryEngine
import com.sam.assistant.agent.execution.UserApprovalManager
import com.sam.assistant.agent.graph.TaskGraphManager
import com.sam.assistant.agent.memory.AgentMemory
import com.sam.assistant.agent.model.AgentGoal
import com.sam.assistant.agent.model.AgentState
import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.GoalExecutionResult
import com.sam.assistant.agent.planner.AutonomousPlanner
import com.sam.assistant.agent.tools.AlarmTimerTool
import com.sam.assistant.agent.tools.AndroidAppTool
import com.sam.assistant.agent.tools.BrowserResearchTool
import com.sam.assistant.agent.tools.CommunicationTool
import com.sam.assistant.agent.tools.DeviceSettingTool
import com.sam.assistant.agent.tools.DocumentInspectionTool
import com.sam.assistant.agent.tools.MemoryIntelligenceTool
import com.sam.assistant.agent.tools.SecurityAuditTool
import com.sam.assistant.agent.tools.ToolRegistry
import com.sam.assistant.agent.tools.ToolSelector
import com.sam.assistant.agent.tools.YouTubeResearchTool
import com.sam.assistant.automation.apps.AppLauncher
import com.sam.assistant.automation.browser.BrowserAutomationManager
import com.sam.assistant.automation.calls.CallManager
import com.sam.assistant.automation.core.TaskOrchestrator
import com.sam.assistant.automation.device.DeviceController
import com.sam.assistant.automation.documents.DocumentAutomationManager
import com.sam.assistant.automation.messages.MessageManager
import com.sam.assistant.memory.manager.PersonalMemoryManager
import kotlinx.coroutines.flow.StateFlow

class AutonomousAgent(
    val context: Context,
    val planner: AutonomousPlanner = AutonomousPlanner(),
    val graphManager: TaskGraphManager = TaskGraphManager(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val stateManager: AgentStateManager = AgentStateManager(),
    val approvalManager: UserApprovalManager = UserApprovalManager(),
    val recoveryEngine: RecoveryEngine = RecoveryEngine(),
    val agentMemory: AgentMemory? = null
) {
    val toolSelector: ToolSelector = ToolSelector(toolRegistry)
    val orchestrator: ExecutionOrchestrator = ExecutionOrchestrator(
        graphManager = graphManager,
        toolSelector = toolSelector,
        stateManager = stateManager,
        approvalManager = approvalManager,
        recoveryEngine = recoveryEngine
    )

    val state: StateFlow<AgentState> = stateManager.state
    val currentGoal: StateFlow<AgentGoal?> = stateManager.currentGoal
    val currentTask: StateFlow<AgentTask?> = stateManager.currentTask
    val logs: StateFlow<List<String>> = stateManager.logs
    val pendingApproval: StateFlow<UserApprovalManager.ApprovalRequest?> = approvalManager.pendingApproval

    init {
        // Register default tools
        toolRegistry.registerTool(SecurityAuditTool(context))
    }

    /**
     * Registers Stage 7 cybersecurity engine tool capabilities into the agent.
     */
    fun registerCybersecurityEngine(engine: com.sam.assistant.cybersecurity.CybersecurityEngine) {
        toolRegistry.registerTool(SecurityAuditTool(context, engine))
    }

    /**
     * Registers subsystem automation tools into the agent.
     */
    fun registerAutomationSubsystems(
        appLauncher: AppLauncher,
        deviceController: DeviceController,
        browserManager: BrowserAutomationManager,
        callManager: CallManager,
        messageManager: MessageManager,
        documentManager: DocumentAutomationManager,
        personalMemoryManager: PersonalMemoryManager,
        taskOrchestrator: TaskOrchestrator
    ) {
        toolRegistry.registerTool(AndroidAppTool(appLauncher, taskOrchestrator))
        toolRegistry.registerTool(DeviceSettingTool(deviceController, taskOrchestrator))
        toolRegistry.registerTool(AlarmTimerTool(deviceController))
        toolRegistry.registerTool(CommunicationTool(callManager, messageManager))
        toolRegistry.registerTool(BrowserResearchTool(browserManager))
        toolRegistry.registerTool(YouTubeResearchTool(appLauncher))
        toolRegistry.registerTool(DocumentInspectionTool(documentManager))
        toolRegistry.registerTool(MemoryIntelligenceTool(personalMemoryManager))
    }

    /**
     * Main entry point to plan and execute a natural language goal autonomously.
     */
    suspend fun processGoal(prompt: String): GoalExecutionResult {
        stateManager.updateState(AgentState.INTERPRETING_GOAL)
        stateManager.addLog("Received goal: \"$prompt\"")

        // 1. Plan & Decompose
        stateManager.updateState(AgentState.GENERATING_PLAN)
        val (goal, graph) = try {
            planner.createPlan(prompt)
        } catch (e: Exception) {
            stateManager.updateState(AgentState.FAILED)
            return GoalExecutionResult(
                goalId = "error",
                status = AgentState.FAILED,
                completedTasks = 0,
                totalTasks = 0,
                summary = "Failed to decompose goal: ${e.message}",
                failedTaskReason = e.message
            )
        }

        graphManager.initializeWithTasks(graph.getAllTasks())
        stateManager.setCurrentGoal(goal)
        stateManager.addLog("Plan generated with ${graph.getAllTasks().size} subtasks.")

        // 2. Persist Initial Goal Plan to DB
        agentMemory?.persistGoalTasks(goal, graph.getAllTasks())

        // 3. Execute Graph
        val result = orchestrator.executeGoal(goal)

        // 4. Update Persistence with Final Status and Evidence
        agentMemory?.let { memory ->
            for (task in graphManager.graph.getAllTasks()) {
                memory.updateTaskStatus(task)
            }
        }

        return result
    }

    fun approvePendingAction(): Boolean {
        stateManager.addLog("User approved pending high-risk action.")
        return approvalManager.approve()
    }

    fun rejectPendingAction(): Boolean {
        stateManager.addLog("User denied pending high-risk action.")
        return approvalManager.reject()
    }

    fun cancelExecution() {
        orchestrator.cancel()
    }
}
