package com.sam.assistant.ai.cognitive.dialogue

import android.util.Log
import com.sam.assistant.ai.cognitive.context.ContextFrame
import com.sam.assistant.ai.cognitive.context.ContextManager
import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.models.DialogueState
import com.sam.assistant.voice.language.LanguageDetectionManager
import com.sam.assistant.voice.language.SpokenLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveDisambiguation(
    val intent: CognitiveIntent,
    val question: String,
    val options: List<String>,
    val targetProperty: String, // e.g. "contactName", "appName"
    val originalEntities: CognitiveEntities
)

object DialogueManager {

    private const val TAG = "DialogueManager"

    private val _currentState = MutableStateFlow(DialogueState.IDLE)
    val currentState: StateFlow<DialogueState> = _currentState.asStateFlow()

    private var pendingDisambiguation: ActiveDisambiguation? = null

    fun transitionTo(newState: DialogueState, reason: String = "") {
        Log.d(TAG, "Dialogue state: ${_currentState.value} -> $newState ($reason)")
        _currentState.value = newState
    }

    fun hasPendingDisambiguation(): Boolean = pendingDisambiguation != null

    fun getPendingDisambiguation(): ActiveDisambiguation? = pendingDisambiguation

    fun startDisambiguation(
        intent: CognitiveIntent,
        question: String,
        options: List<String>,
        targetProperty: String,
        entities: CognitiveEntities
    ) {
        pendingDisambiguation = ActiveDisambiguation(
            intent = intent,
            question = question,
            options = options,
            targetProperty = targetProperty,
            originalEntities = entities
        )
        transitionTo(DialogueState.NEEDS_CLARIFICATION, "Ambiguity detected")
    }

    /**
     * Attempts to resolve an active disambiguation using the user's reply (e.g. "the second one", "Rahul Verma", "Spotify").
     */
    fun resolveDisambiguation(
        userReply: String,
        ordinal: Int?
    ): Pair<CognitiveIntent, CognitiveEntities>? {
        val active = pendingDisambiguation ?: return null
        transitionTo(DialogueState.RESOLVING_CONTEXT, "Resolving user clarification: $userReply")

        val options = active.options
        var chosenIndex = -1

        // 1. Check ordinal selection (e.g. "the second one" -> 2)
        if (ordinal != null) {
            chosenIndex = if (ordinal == -1) options.size - 1 else ordinal - 1
        } else {
            // 2. Direct string match
            val lower = userReply.trim().lowercase()
            for (i in options.indices) {
                val opt = options[i].lowercase()
                if (lower == opt || lower.contains(opt) || opt.contains(lower)) {
                    chosenIndex = i
                    break
                }
            }
        }

        if (chosenIndex in options.indices) {
            val selectedOption = options[chosenIndex]
            Log.d(TAG, "Disambiguation resolved to option [$chosenIndex]: $selectedOption")

            val updatedEntities = when (active.targetProperty) {
                "contactName" -> active.originalEntities.copy(contactName = selectedOption)
                "appName" -> active.originalEntities.copy(appName = selectedOption)
                else -> active.originalEntities
            }

            clearDisambiguation()
            transitionTo(DialogueState.READY_FOR_ACTION, "Clarification resolved")

            // Update context
            ContextManager.updateContext(
                ContextFrame(
                    intent = active.intent,
                    entities = updatedEntities,
                    rawUtterance = userReply
                )
            )

            return Pair(active.intent, updatedEntities)
        }

        Log.w(TAG, "Could not resolve option from user reply: $userReply")
        return null
    }

    fun clearDisambiguation() {
        pendingDisambiguation = null
    }

    fun reset() {
        pendingDisambiguation = null
        _currentState.value = DialogueState.IDLE
    }

    /**
     * Formats multilingual clarification questions
     */
    fun buildDisambiguationPrompt(
        language: SpokenLanguage,
        contactName: String,
        candidates: List<String>
    ): String {
        val formattedCandidates = candidates.mapIndexed { idx, name -> "${idx + 1}. $name" }.joinToString(", ")
        return when (language) {
            SpokenLanguage.HINDI -> "Mujhe $contactName naam ke do contacts mile: $formattedCandidates. Kise call karoon?"
            SpokenLanguage.HINGLISH -> "I found multiple contacts for $contactName: $formattedCandidates. Which one should I call?"
            SpokenLanguage.ENGLISH -> "I found ${candidates.size} contacts named $contactName: $formattedCandidates. Which one should I call?"
        }
    }
}
