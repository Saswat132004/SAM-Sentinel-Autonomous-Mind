package com.sam.assistant

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.sam.assistant.agent.AutonomousAgent
import com.sam.assistant.agent.memory.AgentMemory
import com.sam.assistant.ai.cognitive.CognitiveOrchestrator
import com.sam.assistant.knowledge.KnowledgeEngine
import com.sam.assistant.ai.cognitive.memory.ConversationMemory
import com.sam.assistant.ai.inference.LocalAiEngine
import com.sam.assistant.ai.intent.FastCommandEngine
import com.sam.assistant.ai.models.ModelManager
import com.sam.assistant.ai.planner.TaskPlanner
import com.sam.assistant.automation.apps.AppLauncher
import com.sam.assistant.automation.calls.CallManager
import com.sam.assistant.automation.device.DeviceController
import com.sam.assistant.automation.executor.ActionExecutor
import com.sam.assistant.automation.messages.MessageManager
import com.sam.assistant.automation.reminders.ReminderScheduler
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.core.security.LockStateReceiver
import com.sam.assistant.memory.database.SamDatabase
import com.sam.assistant.memory.manager.PersonalMemoryManager
import com.sam.assistant.memory.repository.MemoryRepository

class SamApplication : Application() {

    lateinit var database: SamDatabase
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var personalMemoryManager: PersonalMemoryManager
        private set
    lateinit var appLauncher: AppLauncher
        private set
    lateinit var callManager: CallManager
        private set
    lateinit var messageManager: MessageManager
        private set
    lateinit var deviceController: DeviceController
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set
    lateinit var actionExecutor: ActionExecutor
        private set
    val taskOrchestrator: com.sam.assistant.automation.core.TaskOrchestrator
        get() = actionExecutor.orchestrator
    lateinit var fastCommandEngine: FastCommandEngine
        private set
    lateinit var taskPlanner: TaskPlanner
        private set
    lateinit var modelManager: ModelManager
        private set
    lateinit var localAiEngine: LocalAiEngine
        private set
    lateinit var conversationMemory: ConversationMemory
        private set
    lateinit var cognitiveOrchestrator: CognitiveOrchestrator
        private set
    lateinit var autonomousAgent: AutonomousAgent
        private set
    lateinit var knowledgeEngine: KnowledgeEngine
        private set
    lateinit var cybersecurityEngine: com.sam.assistant.cybersecurity.CybersecurityEngine
        private set

    private val lockStateReceiver = LockStateReceiver()

    override fun onCreate() {
        super.onCreate()

        // 1. Database & Repositories
        database = SamDatabase.getInstance(this)
        memoryRepository = MemoryRepository(database)
        personalMemoryManager = PersonalMemoryManager(memoryRepository)

        // 2. Automation Subsystems
        appLauncher = AppLauncher(this)
        callManager = CallManager(this)
        messageManager = MessageManager(this, callManager)
        deviceController = DeviceController(this)
        reminderScheduler = ReminderScheduler(this, memoryRepository)
        actionExecutor = ActionExecutor(
            context = this,
            memoryRepository = memoryRepository,
            appLauncher = appLauncher,
            callManager = callManager,
            messageManager = messageManager,
            deviceController = deviceController,
            reminderScheduler = reminderScheduler,
            personalMemoryManager = personalMemoryManager
        )

        // 3. AI & Fast Intent Engines
        fastCommandEngine = FastCommandEngine()
        taskPlanner = TaskPlanner(fastCommandEngine)
        modelManager = ModelManager(this)
        localAiEngine = LocalAiEngine(this, modelManager, memoryRepository)
        conversationMemory = ConversationMemory(memoryRepository)
        cognitiveOrchestrator = CognitiveOrchestrator(this, conversationMemory)

        // 4. Stage 5: Autonomous Agent Intelligence
        val agentMemory = AgentMemory(database.agentTaskDao())
        autonomousAgent = AutonomousAgent(
            context = this,
            agentMemory = agentMemory
        ).apply {
            registerAutomationSubsystems(
                appLauncher = appLauncher,
                deviceController = deviceController,
                browserManager = actionExecutor.orchestrator.browserManager,
                callManager = callManager,
                messageManager = messageManager,
                documentManager = actionExecutor.orchestrator.documentManager,
                personalMemoryManager = personalMemoryManager,
                taskOrchestrator = actionExecutor.orchestrator
            )
        }

        // 5. Stage 6: Personal Intelligence, Long-Term Memory & Study Assistant
        knowledgeEngine = KnowledgeEngine(
            context = this,
            database = database,
            memoryRepository = memoryRepository,
            reminderScheduler = reminderScheduler
        )

        // 6. Stage 7: Cybersecurity Intelligence & Wireless Network Security
        cybersecurityEngine = com.sam.assistant.cybersecurity.CybersecurityEngine(
            context = this,
            database = database
        ).apply {
            taskOrchestrator.cybersecurityEngine = this
            autonomousAgent.registerCybersecurityEngine(this)
        }

        // 7. Lock-State Security Guard
        registerLockStateReceiver()
        LockStateManager.checkDeviceLockState(this)
    }

    private fun registerLockStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(lockStateReceiver, filter)
    }
}

