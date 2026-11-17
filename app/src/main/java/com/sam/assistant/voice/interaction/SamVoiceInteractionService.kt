package com.sam.assistant.voice.interaction

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log

class SamVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "SamVoiceService"
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "SAM VoiceInteractionService is ready and bound by Android system")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "SAM VoiceInteractionService shutdown")
    }
}

