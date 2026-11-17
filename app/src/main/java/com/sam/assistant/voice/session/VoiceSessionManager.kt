package com.sam.assistant.voice.session

import android.content.Context
import android.util.Log
import com.sam.assistant.ai.intent.SessionContextManager
import com.sam.assistant.ai.planner.TaskPlanner
import com.sam.assistant.automation.executor.ActionExecutor
import com.sam.assistant.automation.executor.ExecutionResult
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.memory.repository.MemoryRepository
import com.sam.assistant.voice.recognition.RecognitionResult
import com.sam.assistant.voice.recognition.SpeechRecognitionManager
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import com.sam.assistant.voice.tts.TextToSpeechManager
import com.sam.assistant.voice.wakeword.WakeWordManager
import kotlinx.coroutines.*

data class SessionTimingConfig(
    val initialSpeechWaitMs: Long = 15000L,
    val endOfSpeechSilenceMs: Long = 2000L,
    val pauseAllowanceMs: Long = 5000L,
    val confirmationTimeoutMs: Long = 15000L,
    val followUpWindowMs: Long = 15000L,
    val maxUtteranceDurationMs: Long = 45000L
)

class VoiceSessionManager(
    private val context: Context,
    private val wakeWordManager: WakeWordManager,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val ttsManager: TextToSpeechManager,
    private val taskPlanner: TaskPlanner,
    private val actionExecutor: ActionExecutor,
    private val memoryRepository: MemoryRepository,
    val timingConfig: SessionTimingConfig = SessionTimingConfig()
) {

    companion object {
        private const val TAG = "VoiceSessionManager"
    }

    private val sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var followUpJob: Job? = null
    private var confirmationTimeoutJob: Job? = null

    init {
        LockStateManager.registerListener { isLocked ->
            if (isLocked) {
                Log.d(TAG, "Device locked -> aborting active voice session immediately.")
                cancelActiveSession()
                VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Device is locked")
            }
        }
    }

    /**
     * Step 1 & 2: User unlocks phone -> SAM enters standby when permitted.
     */
    fun enterStandby() {
        if (LockStateManager.isLocked.value) {
            VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Phone is locked")
            return
        }
        cancelActiveSession()
        wakeWordManager.startStandby(sessionScope)
    }

    /**
     * Step 3, 4 & 5: "Hey SAM" detected -> Acknowledgement ("Yes, I'm listening.") -> Active Listening.
     */
    fun onWakeWordDetected() {
        Log.d(TAG, "Wake-word triggered! Initiating voice session flow...")
        wakeWordManager.stopStandby()
        VoiceStateManager.transitionTo(VoiceState.WAKE_WORD_DETECTED, "Hey SAM matched")

        // Spoken acknowledgement: "Yes, I'm listening."
        ttsManager.speakAcknowledgement {
            startListeningForCommand()
        }
    }

    /**
     * Manual trigger (e.g. tapping the Voice Orb on screen).
     */
    fun triggerManualListening() {
        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Manual listening blocked: Device is locked.")
            return
        }
        wakeWordManager.stopStandby()
        startListeningForCommand()
    }

    private fun startListeningForCommand() {
        cancelFollowUpTimer()
        ttsManager.interrupt()

        VoiceStateManager.transitionTo(VoiceState.LISTENING, "Listening for user command")
        speechRecognitionManager.startListening(sessionScope)
    }

    /**
     * Step 6 & 7: Transcribed utterance received from speech recognition.
     */
    fun onCommandRecognized(result: RecognitionResult) {
        val command = result.text.trim()
        Log.d(TAG, "Utterance transcribed: \"$command\" via ${result.engineUsed}")

        if (command.isBlank()) {
            handleNoSpeechDetected()
            return
        }

        sessionScope.launch {
            // Record user message into private persistent conversation history
            memoryRepository.saveConversation(role = "user", content = command)
            VoiceStateManager.setRecognizedText(command)
            VoiceStateManager.transitionTo(VoiceState.PROCESSING, "Processing command")

            // Plan and execute command
            processCommandPipeline(command)
        }
    }

    private suspend fun processCommandPipeline(commandText: String) {
        val app = context.applicationContext as? com.sam.assistant.SamApplication
        val orchestrator = app?.cognitiveOrchestrator
        val cognitiveResult = orchestrator?.processUtterance(commandText)

        val executionResult = if (cognitiveResult != null) {
            when {
                cognitiveResult.isClarificationNeeded -> {
                    ExecutionResult.NeedsClarification(
                        prompt = cognitiveResult.clarificationPrompt ?: "Could you please clarify?",
                        contextKey = cognitiveResult.intent.name,
                        options = cognitiveResult.clarificationOptions
                    )
                }
                cognitiveResult.requiresConfirmation -> {
                    ExecutionResult.NeedsConfirmation(
                        prompt = cognitiveResult.confirmationPrompt ?: "Are you sure you want to proceed?",
                        request = cognitiveResult.toActionRequest()
                    )
                }
                cognitiveResult.reasoningProposal != null -> {
                    val actions = cognitiveResult.reasoningProposal.executableActions
                    if (actions.isNotEmpty()) {
                        actionExecutor.execute(actions.first())
                    } else {
                        ExecutionResult.Success(
                            message = cognitiveResult.reasoningProposal.objective,
                            spokenResponse = cognitiveResult.reasoningProposal.conciseUserExplanation
                        )
                    }
                }
                cognitiveResult.intent != com.sam.assistant.ai.cognitive.models.CognitiveIntent.UNKNOWN_INTENT -> {
                    val request = cognitiveResult.toActionRequest()
                    actionExecutor.execute(request)
                }
                else -> {
                    val plan = taskPlanner.createPlan(commandText)
                    if (plan.intent != "UNKNOWN") {
                        actionExecutor.executePlan(plan)
                    } else {
                        val (spoken, text) = com.sam.assistant.ai.cognitive.response.ResponseGenerator.generateResponse(cognitiveResult)
                        ExecutionResult.Success(message = text, spokenResponse = spoken)
                    }
                }
            }
        } else {
            val plan = taskPlanner.createPlan(commandText)
            if (plan.intent != "UNKNOWN") {
                actionExecutor.executePlan(plan)
            } else {
                ExecutionResult.Success(
                    message = "I heard: \"$commandText\". You can ask me to open apps, play music in Brave, check battery, or call contacts.",
                    spokenResponse = "I recognized your command: $commandText."
                )
            }
        }

        handleExecutionOutcome(executionResult)
    }

    /**
     * Step 8, 9, 10 & 11: Delivers result through TTS and manages follow-up listening.
     */
    private suspend fun handleExecutionOutcome(result: ExecutionResult) {
        when (result) {
            is ExecutionResult.Success -> {
                memoryRepository.saveConversation(role = "sam", content = result.spokenResponse)
                ttsManager.speak(
                    text = result.spokenResponse,
                    onDone = {
                        VoiceStateManager.transitionTo(VoiceState.COMPLETED, "Action succeeded")
                        startFollowUpWindow()
                    }
                )
            }
            is ExecutionResult.NeedsConfirmation -> {
                VoiceStateManager.transitionTo(VoiceState.WAITING_FOR_CONFIRMATION, "Awaiting user confirmation")
                memoryRepository.saveConversation(role = "sam", content = result.prompt)
                ttsManager.speak(
                    text = result.prompt,
                    onDone = {
                        startConfirmationListening()
                    }
                )
            }
            is ExecutionResult.NeedsClarification -> {
                VoiceStateManager.transitionTo(VoiceState.NEEDS_CLARIFICATION, "Asking clarification question")
                memoryRepository.saveConversation(role = "sam", content = result.prompt)
                ttsManager.speak(
                    text = result.prompt,
                    onDone = {
                        startListeningForCommand()
                    }
                )
            }
            is ExecutionResult.PartialSuccess -> {
                memoryRepository.saveConversation(role = "sam", content = result.spokenResponse)
                ttsManager.speak(
                    text = result.spokenResponse,
                    onDone = {
                        startFollowUpWindow()
                    }
                )
            }
            is ExecutionResult.PermissionRequired -> {
                memoryRepository.saveConversation(role = "sam", content = result.userExplanation)
                ttsManager.speak(
                    text = result.userExplanation,
                    onDone = {
                        returnToStandby()
                    }
                )
            }
            is ExecutionResult.Failed -> {
                memoryRepository.saveConversation(role = "sam", content = result.spokenError)
                ttsManager.speak(
                    text = result.spokenError,
                    onDone = {
                        returnToStandby()
                    }
                )
            }
            is ExecutionResult.Error -> {
                memoryRepository.saveConversation(role = "sam", content = result.spokenError)
                ttsManager.speak(
                    text = result.spokenError,
                    onDone = {
                        returnToStandby()
                    }
                )
            }
        }
    }

    /**
     * Step 11: Follow-up conversational window (15 seconds).
     * SAM remains responsive to natural follow-ups without requiring the wake word again.
     */
    private fun startFollowUpWindow() {
        if (LockStateManager.isLocked.value) {
            returnToStandby()
            return
        }

        VoiceStateManager.transitionTo(VoiceState.FOLLOW_UP_LISTENING, "Waiting for follow-up in active window")
        speechRecognitionManager.startListening(sessionScope)

        cancelFollowUpTimer()
        followUpJob = sessionScope.launch {
            delay(timingConfig.followUpWindowMs)
            if (VoiceStateManager.currentState.value == VoiceState.FOLLOW_UP_LISTENING) {
                Log.d(TAG, "Follow-up window expired without additional input -> returning to standby.")
                returnToStandby()
            }
        }
    }

    private fun startConfirmationListening() {
        speechRecognitionManager.startListening(sessionScope)

        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = sessionScope.launch {
            delay(timingConfig.confirmationTimeoutMs)
            if (VoiceStateManager.currentState.value == VoiceState.WAITING_FOR_CONFIRMATION) {
                Log.d(TAG, "Confirmation timed out after ${timingConfig.confirmationTimeoutMs} ms. Cancelling action.")
                SessionContextManager.clearConfirmation()
                ttsManager.speak("Confirmation timed out. Action cancelled.") {
                    returnToStandby()
                }
            }
        }
    }

    private fun handleNoSpeechDetected() {
        Log.d(TAG, "No speech detected during listening session.")
        returnToStandby()
    }

    /**
     * Step 12: Return to standby passive detection.
     */
    fun returnToStandby() {
        cancelFollowUpTimer()
        speechRecognitionManager.stopListening()
        ttsManager.interrupt()

        if (LockStateManager.isLocked.value) {
            VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Device is locked")
        } else {
            wakeWordManager.startStandby(sessionScope)
        }
    }

    fun cancelActiveSession() {
        cancelFollowUpTimer()
        confirmationTimeoutJob?.cancel()
        speechRecognitionManager.stopListening()
        ttsManager.stop()
    }

    private fun cancelFollowUpTimer() {
        followUpJob?.cancel()
        followUpJob = null
    }

    fun shutdown() {
        cancelActiveSession()
        sessionScope.cancel()
        wakeWordManager.stopStandby()
        ttsManager.shutdown()
    }
}
