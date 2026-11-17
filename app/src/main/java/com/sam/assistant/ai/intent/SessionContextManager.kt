package com.sam.assistant.ai.intent

import com.sam.assistant.ai.planner.TaskPlan
import com.sam.assistant.automation.executor.ActionRequest

data class PendingConfirmation(
    val prompt: String,
    val request: ActionRequest,
    val pendingPlan: TaskPlan? = null
)

data class PendingClarification(
    val prompt: String,
    val contextKey: String,
    val options: List<String>,
    val pendingPlan: TaskPlan? = null
)

object SessionContextManager {

    private var activeConfirmation: PendingConfirmation? = null
    private var activeClarification: PendingClarification? = null
    private var preferredMusicSource: String? = null

    fun setPendingConfirmation(prompt: String, request: ActionRequest, plan: TaskPlan? = null) {
        activeConfirmation = PendingConfirmation(prompt, request, plan)
        activeClarification = null
    }

    fun getPendingConfirmation(): PendingConfirmation? = activeConfirmation

    fun hasPendingConfirmation(): Boolean = activeConfirmation != null

    fun clearConfirmation() {
        activeConfirmation = null
    }

    fun setPendingClarification(prompt: String, contextKey: String, options: List<String>, plan: TaskPlan? = null) {
        activeClarification = PendingClarification(prompt, contextKey, options, plan)
        activeConfirmation = null
    }

    fun getPendingClarification(): PendingClarification? = activeClarification

    fun hasPendingClarification(): Boolean = activeClarification != null

    fun clearClarification() {
        activeClarification = null
    }

    fun clearAll() {
        activeConfirmation = null
        activeClarification = null
    }

    fun setPreferredMusicSource(source: String) {
        preferredMusicSource = source
    }

    fun getPreferredMusicSource(): String? = preferredMusicSource

    fun isAffirmative(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        val affirmatives = setOf(
            "yes", "yeah", "yup", "sure", "ok", "okay", "confirm", "confirmed",
            "proceed", "go ahead", "do it", "call", "send", "please",
            // Hindi / Hinglish affirmatives
            "haan", "ha", "haa", "theek hai", "sahi hai", "bilkul", "kar do", "bhej do", "lagao"
        )
        return affirmatives.contains(trimmed) || trimmed.startsWith("yes ") || trimmed.startsWith("haan ")
    }

    fun isNegative(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        val negatives = setOf(
            "no", "nope", "cancel", "stop", "don't", "dont", "abort", "nevermind", "never mind",
            // Hindi / Hinglish negatives
            "nahi", "na", "mat karo", "rehnde do", "rehne do", "ruk jao", "chhod do"
        )
        return negatives.contains(trimmed) || trimmed.startsWith("no ") || trimmed.startsWith("nahi ")
    }

    fun resolveOption(input: String, options: List<String>): String? {
        val cleaned = input.trim().lowercase()
        for (option in options) {
            val optLower = option.lowercase()
            if (cleaned == optLower || cleaned.contains(optLower) || optLower.contains(cleaned)) {
                return option
            }
        }
        return null
    }
}
