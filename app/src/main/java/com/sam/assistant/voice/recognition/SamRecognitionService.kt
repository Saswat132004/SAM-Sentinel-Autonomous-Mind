package com.sam.assistant.voice.recognition

import android.content.Intent
import android.speech.RecognitionService

class SamRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition callback managed through assistant session
    }

    override fun onCancel(listener: Callback?) {
        // Cancel speech processing
    }

    override fun onStopListening(listener: Callback?) {
        // Complete speech session
    }
}

