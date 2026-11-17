package com.sam.assistant.automation.core

import android.util.Log
import com.sam.assistant.automation.executor.ActionRequest

data class PendingHighRiskAction(
    val prompt: String,
    val request: ActionRequest,
    val riskLevel: RiskLevel,
    val timestampMs: Long = System.currentTimeMillis(),
    val timeoutMs: Long = 15000L
)

object ConfirmationManager {

    private const val TAG = "ConfirmationManager"

    private var activePendingAction: PendingHighRiskAction? = null

    @Synchronized
    fun setPendingAction(prompt: String, request: ActionRequest, riskLevel: RiskLevel = RiskLevel.HIGH) {
        Log.d(TAG, "Registered pending confirmation for: ${request.actionType.name} (Risk: $riskLevel)")
        activePendingAction = PendingHighRiskAction(
            prompt = prompt,
            request = request,
            riskLevel = riskLevel
        )
    }

    @Synchronized
    fun getPendingAction(): PendingHighRiskAction? {
        val current = activePendingAction ?: return null
        if (System.currentTimeMillis() - current.timestampMs > current.timeoutMs) {
            Log.d(TAG, "Pending confirmation expired.")
            activePendingAction = null
            return null
        }
        return current
    }

    @Synchronized
    fun hasPendingAction(): Boolean = getPendingAction() != null

    @Synchronized
    fun clear() {
        activePendingAction = null
    }

    /**
     * Multilingual affirmative verification across English, Hindi, and Hinglish.
     */
    fun isAffirmative(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        val affirmatives = setOf(
            "yes", "yeah", "yup", "sure", "ok", "okay", "confirm", "confirmed",
            "proceed", "go ahead", "do it", "call", "send", "please",
            // Hindi / Hinglish
            "haan", "ha", "haa", "theek hai", "sahi hai", "bilkul", "kar do", "bhej do", "lagao"
        )
        return affirmatives.contains(trimmed) || trimmed.startsWith("yes ") || trimmed.startsWith("haan ")
    }

    /**
     * Multilingual negative verification across English, Hindi, and Hinglish.
     */
    fun isNegative(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        val negatives = setOf(
            "no", "nope", "cancel", "stop", "don't", "dont", "abort", "nevermind", "never mind",
            // Hindi / Hinglish
            "nahi", "na", "mat karo", "rehnde do", "rehne do", "ruk jao", "chhod do"
        )
        return negatives.contains(trimmed) || trimmed.startsWith("no ") || trimmed.startsWith("nahi ")
    }
}
