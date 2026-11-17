package com.sam.assistant.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sam.assistant.SamApplication
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.ui.navigation.SamDestinations
import com.sam.assistant.ui.navigation.SamNavHost
import com.sam.assistant.ui.theme.SAMTheme
import com.sam.assistant.ui.theme.SamDarkBackground
import com.sam.assistant.voice.interaction.SamAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    var assistantService: SamAssistantService? = null
        private set
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SamAssistantService.LocalBinder
            assistantService = binder?.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            assistantService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind to background assistant service
        val serviceIntent = Intent(this, SamAssistantService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        val hasAudio = PermissionManager.hasRecordAudioPermission(this)
        val isAssistIntent = intent?.action == Intent.ACTION_ASSIST
        val autoListen = intent?.getBooleanExtra("AUTO_LISTEN", false) ?: false

        val startDest = when {
            !hasAudio -> SamDestinations.ONBOARDING
            isAssistIntent || autoListen -> SamDestinations.VOICE
            else -> SamDestinations.DASHBOARD
        }

        if (hasAudio) {
            SamAssistantService.start(this)
        }

        setContent {
            SAMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SamDarkBackground
                ) {
                    val navController = rememberNavController()
                    SamNavHost(
                        navController = navController,
                        startDestination = startDest
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("AUTO_LISTEN", false)) {
            assistantService?.triggerManualVoiceInput()
        }
    }

    fun sendManualCommand(commandText: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val app = application as SamApplication
            // Save to chat
            app.memoryRepository.saveConversation(role = "user", content = commandText)
            
            val plan = app.taskPlanner.createPlan(commandText)
            val responseText: String
            if (plan.intent != "UNKNOWN") {
                val execResult = app.actionExecutor.executePlan(plan)
                responseText = when (execResult) {
                    is com.sam.assistant.automation.executor.ExecutionResult.Success -> execResult.spokenResponse
                    is com.sam.assistant.automation.executor.ExecutionResult.PartialSuccess -> execResult.spokenResponse
                    is com.sam.assistant.automation.executor.ExecutionResult.NeedsConfirmation -> execResult.prompt
                    is com.sam.assistant.automation.executor.ExecutionResult.NeedsClarification -> execResult.prompt
                    is com.sam.assistant.automation.executor.ExecutionResult.PermissionRequired -> execResult.userExplanation
                    is com.sam.assistant.automation.executor.ExecutionResult.Failed -> execResult.spokenError
                    is com.sam.assistant.automation.executor.ExecutionResult.Error -> execResult.spokenError
                }
            } else {
                val res = app.localAiEngine.generateResponse(commandText)
                responseText = when (res) {
                    is com.sam.assistant.ai.inference.AiResponse.Text -> res.reply
                    is com.sam.assistant.ai.inference.AiResponse.Action -> {
                        val execRes = app.actionExecutor.execute(res.request)
                        when (execRes) {
                            is com.sam.assistant.automation.executor.ExecutionResult.Success -> execRes.spokenResponse
                            is com.sam.assistant.automation.executor.ExecutionResult.PartialSuccess -> execRes.spokenResponse
                            is com.sam.assistant.automation.executor.ExecutionResult.NeedsConfirmation -> execRes.prompt
                            is com.sam.assistant.automation.executor.ExecutionResult.NeedsClarification -> execRes.prompt
                            is com.sam.assistant.automation.executor.ExecutionResult.PermissionRequired -> execRes.userExplanation
                            is com.sam.assistant.automation.executor.ExecutionResult.Failed -> execRes.spokenError
                            is com.sam.assistant.automation.executor.ExecutionResult.Error -> execRes.spokenError
                        }
                    }
                }
            }
            app.memoryRepository.saveConversation(role = "sam", content = responseText)
            com.sam.assistant.voice.state.VoiceStateManager.setSpokenText(responseText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}

