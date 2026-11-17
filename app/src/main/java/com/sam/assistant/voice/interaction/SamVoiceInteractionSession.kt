package com.sam.assistant.voice.interaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.ui.MainActivity

class SamVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "SamVoiceSession"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SAM Assistant Session created")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "SAM Voice Assistant triggered via system assist gesture")

        // Enforce lock-state security: If device is locked, dismiss session immediately
        if (LockStateManager.checkDeviceLockState(context)) {
            Log.w(TAG, "Assistant assist triggered while locked. Terminating session for privacy.")
            finish()
            return
        }

        // Launch the SAM interactive voice HUD
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            putExtra("AUTO_LISTEN", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
        finish()
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "SAM Assistant Session hidden")
    }
}

