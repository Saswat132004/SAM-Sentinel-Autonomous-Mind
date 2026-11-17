package com.sam.assistant.voice.interaction

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sam.assistant.SamApplication
import com.sam.assistant.core.diagnostics.SystemDiagnostics
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.ui.MainActivity
import com.sam.assistant.voice.audio.AudioSessionManager
import com.sam.assistant.voice.audio.AudioSessionOwner
import com.sam.assistant.voice.recognition.RecognitionResult
import com.sam.assistant.voice.recognition.SpeechRecognitionManager
import com.sam.assistant.voice.session.VoiceSessionManager
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager
import com.sam.assistant.voice.tts.TextToSpeechManager
import com.sam.assistant.voice.wakeword.WakeWordConfig
import com.sam.assistant.voice.wakeword.WakeWordDetector
import com.sam.assistant.voice.wakeword.WakeWordManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

class SamAssistantService : Service() {

    companion object {
        private const val TAG = "SamAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sam_voice_standby"

        // Expose VoiceState through VoiceStateManager
        val assistantVoiceState: StateFlow<VoiceState> = VoiceStateManager.currentState

        fun start(context: Context) {
            val intent = Intent(context, SamAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SamAssistantService::class.java)
            context.stopService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Stage 2 Voice Architecture Components
    lateinit var audioSessionManager: AudioSessionManager
        private set
    lateinit var ttsManager: TextToSpeechManager
        private set
    lateinit var wakeWordDetector: WakeWordDetector
        private set
    lateinit var wakeWordManager: WakeWordManager
        private set
    lateinit var speechRecognitionManager: SpeechRecognitionManager
        private set
    lateinit var voiceSessionManager: VoiceSessionManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): SamAssistantService = this@SamAssistantService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing SAM Stage 2 Voice Engine Foreground Service...")
        startForegroundWithNotification()
        initializeVoiceEngine()
        setupLockSecurityListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggerMicNow = intent?.getBooleanExtra("TRIGGER_MIC_NOW", false) ?: false
        if (triggerMicNow) {
            voiceSessionManager.triggerManualListening()
        } else {
            voiceSessionManager.enterStandby()
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAM Voice Standby Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors local wake-phrase \"Hey SAM\" without cloud connectivity."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAM Offline Voice Engine")
            .setContentText("Standby active: Say \"Hey SAM\" (100% On-Device)")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeVoiceEngine() {
        val app = application as SamApplication

        // 1. Audio Session Gatekeeper
        audioSessionManager = AudioSessionManager(this)

        // 2. Text-to-Speech Manager
        ttsManager = TextToSpeechManager(this)

        // 3. Wake-Word Detector & Manager ("Hey SAM")
        wakeWordDetector = WakeWordDetector(
            audioSessionManager = audioSessionManager,
            config = WakeWordConfig(wakePhrase = "Hey SAM", sensitivity = 0.75f),
            onWakeWordTriggered = {
                voiceSessionManager.onWakeWordDetected()
            }
        )
        wakeWordManager = WakeWordManager(
            context = this,
            audioSessionManager = audioSessionManager,
            config = WakeWordConfig(wakePhrase = "Hey SAM", sensitivity = 0.75f),
            onWakeWordDetected = {
                voiceSessionManager.onWakeWordDetected()
            }
        )

        // 4. Speech Recognition Manager (Offline Vosk ASR + Fallback)
        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            audioSessionManager = audioSessionManager,
            onResult = { result ->
                voiceSessionManager.onCommandRecognized(result)
            },
            onError = { error ->
                Log.w(TAG, "Recognition error callback: $error")
                voiceSessionManager.returnToStandby()
            }
        )

        // 5. Advanced Voice Session Manager (11-State Machine & Adaptive Timers)
        voiceSessionManager = VoiceSessionManager(
            context = this,
            wakeWordManager = wakeWordManager,
            speechRecognitionManager = speechRecognitionManager,
            ttsManager = ttsManager,
            taskPlanner = app.taskPlanner,
            actionExecutor = app.actionExecutor,
            memoryRepository = app.memoryRepository
        )

        // Audio interruption handling
        audioSessionManager.setInterruptionListener { isInterrupted ->
            if (isInterrupted) {
                Log.w(TAG, "System audio interruption: Pausing voice engine")
                voiceSessionManager.cancelActiveSession()
                VoiceStateManager.transitionTo(VoiceState.STANDBY, "Interrupted by system audio")
            } else {
                Log.d(TAG, "Audio interruption cleared: Resuming standby")
                voiceSessionManager.enterStandby()
            }
        }
    }

    private fun setupLockSecurityListener() {
        LockStateManager.registerListener { isLocked ->
            if (isLocked) {
                Log.d(TAG, "Security barrier: Device locked. Halting microphone and resetting voice engine.")
                voiceSessionManager.cancelActiveSession()
                audioSessionManager.releaseMicrophone(AudioSessionOwner.NONE)
                VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Device locked")
            } else {
                Log.d(TAG, "Security barrier: Device unlocked. Returning to standby.")
                voiceSessionManager.enterStandby()
            }
        }
    }

    fun startWakeWordStandby() {
        voiceSessionManager.enterStandby()
    }

    fun triggerManualVoiceInput() {
        voiceSessionManager.triggerManualListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        voiceSessionManager.shutdown()
        audioSessionManager.releaseMicrophone(AudioSessionOwner.NONE)
        VoiceStateManager.transitionTo(VoiceState.INACTIVE, "Service destroyed")
        Log.d(TAG, "SAM Assistant Service destroyed")
    }
}
