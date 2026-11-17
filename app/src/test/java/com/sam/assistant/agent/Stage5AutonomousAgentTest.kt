package com.sam.assistant.agent

import com.sam.assistant.agent.execution.*
import com.sam.assistant.agent.graph.TaskGraph
import com.sam.assistant.agent.graph.TaskGraphManager
import com.sam.assistant.agent.memory.AgentMemory
import com.sam.assistant.agent.memory.AgentTaskEntity
import com.sam.assistant.agent.model.*
import com.sam.assistant.agent.planner.AutonomousPlanner
import com.sam.assistant.agent.planner.GoalInterpreter
import com.sam.assistant.agent.planner.TaskDecomposer
import com.sam.assistant.agent.tools.*
import com.sam.assistant.automation.core.RiskLevel
import com.sam.assistant.automation.executor.ActionType
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Stage5AutonomousAgentTest {

    private lateinit var goalInterpreter: GoalInterpreter
    private lateinit var taskDecomposer: TaskDecomposer
    private lateinit var planner: AutonomousPlanner
    private lateinit var recoveryEngine: RecoveryEngine

    @Before
    fun setUp() {
        goalInterpreter = GoalInterpreter()
        taskDecomposer = TaskDecomposer()
        planner = AutonomousPlanner(goalInterpreter, taskDecomposer)
        recoveryEngine = RecoveryEngine()
    }

    // ==========================================
    // 1. Goal Interpreter Tests
    // ==========================================

    @Test
    fun testGoalInterpreterExtractsAppAndQuery() {
        val prompt = "Open Brave and search for the latest cybersecurity news"
        val goal = goalInterpreter.interpretGoal(prompt)

        assertEquals("brave", goal.entities["targetApp"])
        assertEquals("the latest cybersecurity news", goal.entities["query"])
        assertTrue(goal.requiredTools.contains("BrowserResearchTool"))
        assertFalse(goal.isSensitive)
        assertFalse(goal.requiresApproval)
    }

    @Test
    fun testGoalInterpreterIdentifiesSensitivityAndApproval() {
        val callPrompt = "Call Rahul"
        val callGoal = goalInterpreter.interpretGoal(callPrompt)

        assertTrue(callGoal.isSensitive)
        assertTrue(callGoal.requiresApproval)
        assertEquals("Rahul", callGoal.entities["recipient"])

        val smsPrompt = "Send SMS to 9876543210 saying Hello"
        val smsGoal = goalInterpreter.interpretGoal(smsPrompt)
        assertTrue(smsGoal.isSensitive)
    }

    @Test
    fun testGoalInterpreterDetectsMissingInfo() {
        val prompt = "Send message"
        val goal = goalInterpreter.interpretGoal(prompt)

        assertTrue(goal.missingInfo.contains("recipient"))
        assertNotNull(goal.clarificationPrompt)
    }

    // ==========================================
    // 2. Task Decomposer & DAG Tests
    // ==========================================

    @Test
    fun testTaskDecomposerBraveMusicPipeline() {
        val goal = goalInterpreter.interpretGoal("Play some music using Brave Browser")
        val tasks = taskDecomposer.decomposeGoal(goal)

        assertEquals(3, tasks.size)
        // Step 1: verify app
        assertEquals(ActionType.VERIFY_APP_INSTALLED, tasks[0].actionType)
        assertTrue(tasks[0].dependencies.isEmpty())

        // Step 2: launch app
        assertEquals(ActionType.LAUNCH_APP, tasks[1].actionType)
        assertEquals(listOf(tasks[0].taskId), tasks[1].dependencies)

        // Step 3: play media
        assertEquals(ActionType.PLAY_MEDIA, tasks[2].actionType)
        assertEquals(listOf(tasks[1].taskId), tasks[2].dependencies)
    }

    @Test
    fun testTaskDecomposerBraveSearchPipeline() {
        val goal = goalInterpreter.interpretGoal("Open Brave and search for cybersecurity news")
        val tasks = taskDecomposer.decomposeGoal(goal)

        assertEquals(2, tasks.size)
        assertEquals(ActionType.VERIFY_APP_INSTALLED, tasks[0].actionType)
        assertEquals(ActionType.SEARCH_WEB, tasks[1].actionType)
        assertEquals(listOf(tasks[0].taskId), tasks[1].dependencies)
    }

    @Test
    fun testTaskDecomposerYouTubeSearch() {
        val goal = goalInterpreter.interpretGoal("Open YouTube and search for Python tutorials")
        val tasks = taskDecomposer.decomposeGoal(goal)

        assertEquals(2, tasks.size)
        assertEquals(ActionType.VERIFY_APP_INSTALLED, tasks[0].actionType)
        assertEquals(ActionType.SEARCH_YOUTUBE, tasks[1].actionType)
        assertEquals(listOf(tasks[0].taskId), tasks[1].dependencies)
    }

    @Test
    fun testTaskDecomposerWhatsApp() {
        val goal = goalInterpreter.interpretGoal("WhatsApp kholo aur Rahul ko message bhejne ke liye prepare karo")
        val tasks = taskDecomposer.decomposeGoal(goal)

        assertEquals(2, tasks.size)
        assertEquals(ActionType.VERIFY_APP_INSTALLED, tasks[0].actionType)
        assertEquals(ActionType.PREPARE_WHATSAPP, tasks[1].actionType)
        assertTrue(tasks[1].requiresApproval)
        assertEquals(listOf(tasks[0].taskId), tasks[1].dependencies)
    }

    @Test
    fun testTaskDecomposerHighRiskOperations() {
        val callGoal = goalInterpreter.interpretGoal("Call Rahul")
        val callTasks = taskDecomposer.decomposeGoal(callGoal)

        assertEquals(1, callTasks.size)
        assertEquals(RiskLevel.HIGH, callTasks[0].riskLevel)
        assertTrue(callTasks[0].requiresApproval)
        assertEquals(ActionType.MAKE_CALL, callTasks[0].actionType)

        val smsGoal = goalInterpreter.interpretGoal("Send SMS to 9876543210")
        val smsTasks = taskDecomposer.decomposeGoal(smsGoal)

        assertEquals(1, smsTasks.size)
        assertEquals(RiskLevel.HIGH, smsTasks[0].riskLevel)
        assertTrue(smsTasks[0].requiresApproval)
        assertEquals(ActionType.SEND_SMS, smsTasks[0].actionType)
    }

    @Test
    fun testTaskDecomposerDeviceControls() {
        val torchGoal = goalInterpreter.interpretGoal("Turn on flashlight")
        val torchTasks = taskDecomposer.decomposeGoal(torchGoal)
        assertEquals(1, torchTasks.size)
        assertEquals(ActionType.TOGGLE_FLASHLIGHT, torchTasks[0].actionType)
        assertEquals(RiskLevel.LOW, torchTasks[0].riskLevel)

        val battGoal = goalInterpreter.interpretGoal("Check battery status")
        val battTasks = taskDecomposer.decomposeGoal(battGoal)
        assertEquals(1, battTasks.size)
        assertEquals(ActionType.READ_BATTERY, battTasks[0].actionType)
    }

    // ==========================================
    // 3. Task Graph (DAG) Management Tests
    // ==========================================

    @Test
    fun testTaskGraphCycleDetectionValidDAG() {
        val graph = TaskGraph()
        val t1 = AgentTask("t1", "g1", description = "T1", actionType = ActionType.VERIFY_APP_INSTALLED, toolName = "T")
        val t2 = AgentTask("t2", "g1", description = "T2", actionType = ActionType.LAUNCH_APP, toolName = "T", dependencies = listOf("t1"))
        val t3 = AgentTask("t3", "g1", description = "T3", actionType = ActionType.SEARCH_WEB, toolName = "T", dependencies = listOf("t2"))

        graph.addTask(t1)
        graph.addTask(t2)
        graph.addTask(t3)

        assertFalse(graph.hasCycle())
        val order = graph.getTopologicalOrder()
        assertEquals(listOf("t1", "t2", "t3"), order.map { it.taskId })
    }

    @Test
    fun testTaskGraphCycleDetectionCycleDetected() {
        val graph = TaskGraph()
        // Cyclic: t1 -> t2 -> t3 -> t1
        val t1 = AgentTask("t1", "g1", description = "T1", actionType = ActionType.VERIFY_APP_INSTALLED, toolName = "T", dependencies = listOf("t3"))
        val t2 = AgentTask("t2", "g1", description = "T2", actionType = ActionType.LAUNCH_APP, toolName = "T", dependencies = listOf("t1"))
        val t3 = AgentTask("t3", "g1", description = "T3", actionType = ActionType.SEARCH_WEB, toolName = "T", dependencies = listOf("t2"))

        graph.addTask(t1)
        graph.addTask(t2)
        graph.addTask(t3)

        assertTrue(graph.hasCycle())
        assertThrows(IllegalStateException::class.java) {
            graph.getTopologicalOrder()
        }
    }

    @Test
    fun testTaskGraphManagerReadyTaskResolution() {
        val manager = TaskGraphManager()
        val t1 = AgentTask("t1", "g1", description = "T1", actionType = ActionType.VERIFY_APP_INSTALLED, toolName = "T")
        val t2 = AgentTask("t2", "g1", description = "T2", actionType = ActionType.LAUNCH_APP, toolName = "T", dependencies = listOf("t1"))

        manager.initializeWithTasks(listOf(t1, t2))

        val ready1 = manager.getReadyTasks()
        assertEquals(1, ready1.size)
        assertEquals("t1", ready1.first().taskId)

        // Complete t1
        manager.markTaskStatus("t1", TaskStatus.COMPLETED, evidence = "Package found")

        val ready2 = manager.getReadyTasks()
        assertEquals(1, ready2.size)
        assertEquals("t2", ready2.first().taskId)
    }

    @Test
    fun testTaskGraphManagerProgressCalculation() {
        val manager = TaskGraphManager()
        val t1 = AgentTask("t1", "g1", description = "T1", actionType = ActionType.VERIFY_APP_INSTALLED, toolName = "T")
        val t2 = AgentTask("t2", "g1", description = "T2", actionType = ActionType.LAUNCH_APP, toolName = "T", dependencies = listOf("t1"))

        manager.initializeWithTasks(listOf(t1, t2))
        assertEquals(0f, manager.getProgressPercentage(), 0.01f)

        manager.markTaskStatus("t1", TaskStatus.COMPLETED)
        assertEquals(50f, manager.getProgressPercentage(), 0.01f)

        manager.markTaskStatus("t2", TaskStatus.COMPLETED)
        assertEquals(100f, manager.getProgressPercentage(), 0.01f)
        assertTrue(manager.isFinished())
        assertFalse(manager.hasFailures())
    }

    // ==========================================
    // 4. Tool Architecture & Registry Tests
    // ==========================================

    @Test
    fun testToolRegistryAndToolSelector() {
        val registry = ToolRegistry()
        val dummyTool = object : AgentTool {
            override val name: String = "AndroidAppTool"
            override val category: ToolCategory = ToolCategory.ANDROID
            override val description: String = "Test App Tool"
            override val riskLevel: RiskLevel = RiskLevel.LOW
            override suspend fun execute(parameters: Map<String, String>, confirmed: Boolean): ToolExecutionResult {
                return ToolExecutionResult.Success(evidence = "Test Evidence", userMessage = "Success")
            }
        }
        registry.registerTool(dummyTool)

        assertTrue(registry.hasTool("AndroidAppTool"))
        assertEquals(1, registry.getToolsByCategory(ToolCategory.ANDROID).size)

        val selector = ToolSelector(registry)
        val task = AgentTask("t", "g", description = "Launch App", actionType = ActionType.LAUNCH_APP, toolName = "AndroidAppTool")
        val selected = selector.selectToolForTask(task)
        assertNotNull(selected)
        assertEquals("AndroidAppTool", selected?.name)
    }

    // ==========================================
    // 5. Execution Orchestrator & Approval Tests
    // ==========================================

    @Test
    fun testExecutionOrchestratorSuccessFlow() = runBlocking {
        val graphManager = TaskGraphManager()
        val t1 = AgentTask("t1", "g1", description = "Verify", actionType = ActionType.VERIFY_APP_INSTALLED, toolName = "MockTool")
        val t2 = AgentTask("t2", "g1", description = "Execute", actionType = ActionType.SEARCH_WEB, toolName = "MockTool", dependencies = listOf("t1"))
        graphManager.initializeWithTasks(listOf(t1, t2))

        val registry = ToolRegistry()
        registry.registerTool(object : AgentTool {
            override val name: String = "MockTool"
            override val category: ToolCategory = ToolCategory.ANDROID
            override val description: String = "Mock"
            override val riskLevel: RiskLevel = RiskLevel.LOW
            override suspend fun execute(parameters: Map<String, String>, confirmed: Boolean): ToolExecutionResult {
                return ToolExecutionResult.Success(evidence = "Step Evidence Verified", userMessage = "OK")
            }
        })

        val stateManager = AgentStateManager()
        val approvalManager = UserApprovalManager()
        val orchestrator = ExecutionOrchestrator(
            graphManager = graphManager,
            toolSelector = ToolSelector(registry),
            stateManager = stateManager,
            approvalManager = approvalManager,
            recoveryEngine = recoveryEngine
        )

        val goal = AgentGoal("g1", "raw", "main", listOf("t1", "t2"))
        val result = orchestrator.executeGoal(goal)

        assertEquals(AgentState.COMPLETED, result.status)
        assertEquals(2, result.completedTasks)
        assertEquals(2, result.totalTasks)
        assertEquals(2, result.evidenceSummary.size)
    }

    @Test
    fun testExecutionOrchestratorApprovalGateApproved() = runBlocking {
        val graphManager = TaskGraphManager()
        val highRiskTask = AgentTask(
            taskId = "t_call",
            goalId = "g_call",
            description = "Call Contact",
            actionType = ActionType.MAKE_CALL,
            toolName = "MockCallTool",
            riskLevel = RiskLevel.HIGH,
            requiresApproval = true
        )
        graphManager.initializeWithTasks(listOf(highRiskTask))

        val registry = ToolRegistry()
        registry.registerTool(object : AgentTool {
            override val name: String = "MockCallTool"
            override val category: ToolCategory = ToolCategory.ANDROID
            override val description: String = "Mock Call"
            override val riskLevel: RiskLevel = RiskLevel.HIGH
            override suspend fun execute(parameters: Map<String, String>, confirmed: Boolean): ToolExecutionResult {
                return if (confirmed) {
                    ToolExecutionResult.Success(evidence = "Call Placed Successfully", userMessage = "Calling...")
                } else {
                    ToolExecutionResult.RequiresApproval("Please confirm call", parameters)
                }
            }
        })

        val stateManager = AgentStateManager()
        val approvalManager = UserApprovalManager()
        val orchestrator = ExecutionOrchestrator(
            graphManager = graphManager,
            toolSelector = ToolSelector(registry),
            stateManager = stateManager,
            approvalManager = approvalManager,
            recoveryEngine = recoveryEngine
        )

        // Launch in background coroutine so we can approve
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            orchestrator.executeGoal(AgentGoal("g_call", "call", "Call", listOf("t_call")))
        }

        // Wait until approval is requested
        var waited = 0
        while (!approvalManager.hasPending() && waited < 20) {
            kotlinx.coroutines.delay(50)
            waited++
        }

        assertTrue(approvalManager.hasPending())
        val approved = approvalManager.approve()
        assertTrue(approved)

        job.join()
        assertEquals(TaskStatus.COMPLETED, highRiskTask.status)
    }

    @Test
    fun testExecutionOrchestratorApprovalGateDenied() = runBlocking {
        val graphManager = TaskGraphManager()
        val highRiskTask = AgentTask(
            taskId = "t_del",
            goalId = "g_del",
            description = "Delete Database",
            actionType = ActionType.DELETE_MEMORY,
            toolName = "MockTool",
            riskLevel = RiskLevel.HIGH,
            requiresApproval = true
        )
        graphManager.initializeWithTasks(listOf(highRiskTask))

        val registry = ToolRegistry()
        registry.registerTool(object : AgentTool {
            override val name: String = "MockTool"
            override val category: ToolCategory = ToolCategory.INTELLIGENCE
            override val description: String = "Mock"
            override val riskLevel: RiskLevel = RiskLevel.HIGH
            override suspend fun execute(parameters: Map<String, String>, confirmed: Boolean): ToolExecutionResult {
                return ToolExecutionResult.Success(evidence = "Deleted", userMessage = "Deleted")
            }
        })

        val stateManager = AgentStateManager()
        val approvalManager = UserApprovalManager()
        val orchestrator = ExecutionOrchestrator(
            graphManager = graphManager,
            toolSelector = ToolSelector(registry),
            stateManager = stateManager,
            approvalManager = approvalManager,
            recoveryEngine = recoveryEngine
        )

        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            orchestrator.executeGoal(AgentGoal("g_del", "delete", "Delete", listOf("t_del")))
        }

        var waited = 0
        while (!approvalManager.hasPending() && waited < 20) {
            kotlinx.coroutines.delay(50)
            waited++
        }

        assertTrue(approvalManager.hasPending())
        approvalManager.reject()

        job.join()
        assertEquals(TaskStatus.CANCELLED, highRiskTask.status)
        assertEquals("Denied by user.", highRiskTask.errorMessage)
    }

    // ==========================================
    // 6. Recovery Engine Tests
    // ==========================================

    @Test
    fun testRecoveryEngineRetryWithBackoff() {
        val task = AgentTask(
            taskId = "t_retry",
            goalId = "g",
            description = "Network search",
            actionType = ActionType.SEARCH_WEB,
            toolName = "BrowserTool",
            retryPolicy = RetryPolicy(maxRetries = 2, backoffDelayMs = 500)
        )
        val graphManager = TaskGraphManager()

        val failure = ToolExecutionResult.Failed(
            reason = "Temporary network timeout",
            isRetryable = true,
            failingTool = "BrowserTool"
        )

        val action1 = recoveryEngine.determineRecovery(task, failure, graphManager)
        assertTrue(action1 is RecoveryAction.Retry)
        assertEquals(500L, (action1 as RecoveryAction.Retry).delayMs)

        val action2 = recoveryEngine.determineRecovery(task, failure, graphManager)
        assertTrue(action2 is RecoveryAction.Retry)
        assertEquals(1000L, (action2 as RecoveryAction.Retry).delayMs)

        // Third failure exceeds maxRetries (2)
        val action3 = recoveryEngine.determineRecovery(task, failure, graphManager)
        assertTrue(action3 is RecoveryAction.Abort)
    }

    @Test
    fun testRecoveryEngineFallbackToolSubstitution() {
        val task = AgentTask(
            taskId = "t_brave",
            goalId = "g",
            description = "Search in Brave",
            actionType = ActionType.SEARCH_WEB,
            toolName = "BrowserTool",
            inputParameters = mapOf("target" to "brave", "query" to "cybersecurity"),
            retryPolicy = RetryPolicy(maxRetries = 0, allowAlternativeTool = true)
        )
        val graphManager = TaskGraphManager()

        val failure = ToolExecutionResult.Failed(
            reason = "Brave Browser not found",
            isRetryable = false,
            failingTool = "BrowserTool",
            suggestedAlternative = "chrome"
        )

        val action = recoveryEngine.determineRecovery(task, failure, graphManager)
        assertTrue(action is RecoveryAction.SubstituteTool)
        val substituted = (action as RecoveryAction.SubstituteTool).alternativeTask
        assertEquals("chrome", substituted.inputParameters["target"])
        assertTrue(substituted.description.contains("Fallback: chrome"))
    }

    // ==========================================
    // 7. Cancellation & Export Tests
    // ==========================================

    @Test
    fun testExecutionOrchestratorCancellation() = runBlocking {
        val graphManager = TaskGraphManager()
        val t1 = AgentTask("t1", "g1", description = "Long Task 1", actionType = ActionType.SEARCH_WEB, toolName = "MockTool")
        val t2 = AgentTask("t2", "g1", description = "Long Task 2", actionType = ActionType.SEARCH_WEB, toolName = "MockTool", dependencies = listOf("t1"))
        graphManager.initializeWithTasks(listOf(t1, t2))

        val registry = ToolRegistry()
        registry.registerTool(object : AgentTool {
            override val name: String = "MockTool"
            override val category: ToolCategory = ToolCategory.RESEARCH
            override val description: String = "Mock"
            override val riskLevel: RiskLevel = RiskLevel.LOW
            override suspend fun execute(parameters: Map<String, String>, confirmed: Boolean): ToolExecutionResult {
                kotlinx.coroutines.delay(100)
                return ToolExecutionResult.Success(evidence = "Done", userMessage = "Done")
            }
        })

        val stateManager = AgentStateManager()
        val approvalManager = UserApprovalManager()
        val orchestrator = ExecutionOrchestrator(
            graphManager = graphManager,
            toolSelector = ToolSelector(registry),
            stateManager = stateManager,
            approvalManager = approvalManager,
            recoveryEngine = recoveryEngine
        )

        orchestrator.cancel()
        val result = orchestrator.executeGoal(AgentGoal("g1", "prompt", "Long Goal", listOf("t1", "t2")))

        assertEquals(AgentState.CANCELLED, result.status)
        assertEquals(0, result.completedTasks)
    }

    @Test
    fun testAgentMemoryFormatAndExport() {
        val entity1 = AgentTaskEntity(
            id = 1,
            goalId = "g_101",
            taskId = "t_1",
            description = "Verify Brave Installed",
            toolName = "AndroidAppTool",
            status = "COMPLETED",
            riskLevel = "LOW",
            dependencies = "",
            executionEvidence = "Package com.brave.browser verified"
        )
        val entity2 = AgentTaskEntity(
            id = 2,
            goalId = "g_101",
            taskId = "t_2",
            description = "Search Web",
            toolName = "BrowserResearchTool",
            status = "COMPLETED",
            riskLevel = "LOW",
            dependencies = "t_1",
            executionEvidence = "Dispatched search intent"
        )

        val memory = AgentMemory(object : com.sam.assistant.agent.memory.AgentTaskDao {
            override suspend fun insertTask(task: AgentTaskEntity): Long = 1L
            override suspend fun insertTasks(tasks: List<AgentTaskEntity>) {}
            override suspend fun updateTask(task: AgentTaskEntity) {}
            override suspend fun getTasksForGoal(goalId: String): List<AgentTaskEntity> = listOf(entity1, entity2)
            override suspend fun getAllTasks(): List<AgentTaskEntity> = listOf(entity1, entity2)
            override suspend fun deleteTasksForGoal(goalId: String) {}
            override suspend fun clearAllTasks() {}
        })

        val export = memory.formatTasksForExport(listOf(entity1, entity2))
        assertTrue(export.contains("SAM AUTONOMOUS AGENT TASK EXECUTION LOG"))
        assertTrue(export.contains("t_1"))
        assertTrue(export.contains("Verify Brave Installed"))
        assertTrue(export.contains("Package com.brave.browser verified"))
    }
}
