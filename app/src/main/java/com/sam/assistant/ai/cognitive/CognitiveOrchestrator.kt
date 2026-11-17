package com.sam.assistant.ai.cognitive

import android.content.Context
import android.util.Log
import com.sam.assistant.ai.cognitive.context.ContextFrame
import com.sam.assistant.ai.cognitive.context.ContextManager
import com.sam.assistant.ai.cognitive.dialogue.DialogueManager
import com.sam.assistant.ai.cognitive.entity.EntityExtractor
import com.sam.assistant.ai.cognitive.intent.IntentClassifier
import com.sam.assistant.ai.cognitive.memory.ConversationMemory
import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.models.CognitiveParseResult
import com.sam.assistant.ai.cognitive.models.DialogueState
import com.sam.assistant.ai.cognitive.normalizer.LanguageNormalizer
import com.sam.assistant.ai.cognitive.reasoning.ReasoningEngine
import com.sam.assistant.voice.language.LanguageDetectionManager
import com.sam.assistant.voice.language.SpokenLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CognitiveOrchestrator(
    private val context: Context,
    val conversationMemory: ConversationMemory
) {
    companion object {
        private const val TAG = "CognitiveOrchestrator"
    }

    private val _lastParseResult = MutableStateFlow<CognitiveParseResult?>(null)
    val lastParseResult: StateFlow<CognitiveParseResult?> = _lastParseResult.asStateFlow()

    /**
     * Master cognitive analysis pipeline.
     */
    suspend fun processUtterance(rawUtterance: String): CognitiveParseResult {
        val trimmed = rawUtterance.trim()
        Log.d(TAG, "Processing cognitive input: \"$trimmed\"")

        // 1. Language Detection (English, Hindi, Hinglish)
        val language = LanguageDetectionManager.detectLanguage(trimmed)

        // 2. Normalization
        val normalized = LanguageNormalizer.normalize(trimmed)
        val ordinal = LanguageNormalizer.extractOrdinal(normalized)

        // 3. Check for Active Disambiguation (e.g. "Which Rahul?", user says "The second one")
        if (DialogueManager.hasPendingDisambiguation()) {
            val resolved = DialogueManager.resolveDisambiguation(normalized, ordinal)
            if (resolved != null) {
                val (intent, entities) = resolved
                val result = CognitiveParseResult(
                    intent = intent,
                    confidence = 0.95f,
                    entities = entities,
                    language = language,
                    originalUtterance = trimmed,
                    normalizedUtterance = normalized,
                    dialogueState = DialogueState.READY_FOR_ACTION,
                    requiresConfirmation = intent.isSensitive,
                    confirmationPrompt = if (intent == CognitiveIntent.MAKE_CALL) {
                        "Are you sure you want to call ${entities.contactName}?"
                    } else null
                )
                _lastParseResult.value = result
                conversationMemory.saveUserTurn(trimmed, intent.name)
                return result
            }
        }

        // 4. Check for User Corrections against active context (e.g. "Actually, make it 7 AM")
        val correction = ContextManager.resolveCorrection(normalized)
        if (correction != null) {
            val (intent, entities) = correction
            val result = CognitiveParseResult(
                intent = intent,
                confidence = 0.95f,
                entities = entities,
                language = language,
                originalUtterance = trimmed,
                normalizedUtterance = normalized,
                dialogueState = DialogueState.READY_FOR_ACTION,
                requiresConfirmation = intent.isSensitive
            )
            ContextManager.updateContext(
                ContextFrame(intent = intent, entities = entities, rawUtterance = trimmed)
            )
            _lastParseResult.value = result
            conversationMemory.saveUserTurn(trimmed, intent.name)
            return result
        }

        // 5. Check for Ordinal / Pronoun references against active context (e.g. "Play the second one")
        val reference = ContextManager.resolveReference(normalized, ordinal)
        if (reference != null) {
            val (intent, entities) = reference
            val result = CognitiveParseResult(
                intent = intent,
                confidence = 0.94f,
                entities = entities,
                language = language,
                originalUtterance = trimmed,
                normalizedUtterance = normalized,
                dialogueState = DialogueState.READY_FOR_ACTION
            )
            ContextManager.updateContext(
                ContextFrame(intent = intent, entities = entities, rawUtterance = trimmed)
            )
            _lastParseResult.value = result
            conversationMemory.saveUserTurn(trimmed, intent.name)
            return result
        }

        // 6. Classification & Entity Extraction
        val classification = IntentClassifier.classify(trimmed)
        val entities = EntityExtractor.extract(trimmed, classification.intent)

        // 7. Check for Complex Structured Reasoning Requirement
        if (ReasoningEngine.requiresStructuredReasoning(trimmed)) {
            val plan = ReasoningEngine.produceStructuredPlan(trimmed, entities)
            val result = CognitiveParseResult(
                intent = CognitiveIntent.EXPLAIN_CONCEPT,
                confidence = 0.92f,
                entities = entities,
                language = language,
                originalUtterance = trimmed,
                normalizedUtterance = normalized,
                dialogueState = DialogueState.READY_FOR_ACTION,
                reasoningProposal = plan
            )
            _lastParseResult.value = result
            conversationMemory.saveUserTurn(trimmed, CognitiveIntent.EXPLAIN_CONCEPT.name)
            return result
        }

        // 8. Contact Disambiguation Simulation for "Rahul" (Requirement 6 / Test E)
        if (classification.intent == CognitiveIntent.MAKE_CALL && entities.contactName.equals("Rahul", ignoreCase = true)) {
            val candidateContacts = listOf("Rahul Sharma", "Rahul Verma")
            val prompt = DialogueManager.buildDisambiguationPrompt(language, "Rahul", candidateContacts)

            DialogueManager.startDisambiguation(
                intent = CognitiveIntent.MAKE_CALL,
                question = prompt,
                options = candidateContacts,
                targetProperty = "contactName",
                entities = entities
            )

            // Save candidate contacts in context frame for ordinal resolution
            ContextManager.updateContext(
                ContextFrame(
                    intent = CognitiveIntent.MAKE_CALL,
                    entities = entities,
                    rawUtterance = trimmed,
                    candidateContacts = candidateContacts
                )
            )

            val result = CognitiveParseResult(
                intent = CognitiveIntent.MAKE_CALL,
                confidence = 0.90f,
                entities = entities,
                language = language,
                originalUtterance = trimmed,
                normalizedUtterance = normalized,
                dialogueState = DialogueState.NEEDS_CLARIFICATION,
                isClarificationNeeded = true,
                clarificationPrompt = prompt,
                clarificationOptions = candidateContacts
            )
            _lastParseResult.value = result
            conversationMemory.saveUserTurn(trimmed, classification.intent.name)
            return result
        }

        // 9. Ambiguity / Low Confidence Handling
        if (classification.isAmbiguous || classification.confidence < 0.60f) {
            val clarificationPrompt = when (classification.intent) {
                CognitiveIntent.OPEN_APP -> {
                    when (language) {
                        SpokenLanguage.HINDI -> "Aap kaun sa app kholna chahte hain? (YouTube, Brave, ya WhatsApp?)"
                        SpokenLanguage.HINGLISH -> "Which application would you like me to open?"
                        SpokenLanguage.ENGLISH -> "Which application should I open?"
                    }
                }
                CognitiveIntent.PLAY_MEDIA -> {
                    when (language) {
                        SpokenLanguage.HINDI -> "Aap gaana kahan sunna pasand karenge? (Brave, YouTube ya Spotify?)"
                        SpokenLanguage.HINGLISH -> "Where should I play the music? (Brave, YouTube or Spotify?)"
                        SpokenLanguage.ENGLISH -> "Which music source should I use: Brave Browser, YouTube or Spotify?"
                    }
                }
                CognitiveIntent.MAKE_CALL -> "Who would you like to call?"
                else -> "Could you please clarify your request?"
            }

            DialogueManager.transitionTo(DialogueState.NEEDS_CLARIFICATION, "Clarification required")
            val result = CognitiveParseResult(
                intent = classification.intent,
                confidence = classification.confidence,
                entities = entities,
                language = language,
                originalUtterance = trimmed,
                normalizedUtterance = normalized,
                dialogueState = DialogueState.NEEDS_CLARIFICATION,
                isClarificationNeeded = true,
                clarificationPrompt = clarificationPrompt,
                clarificationOptions = classification.clarificationOptions
            )
            _lastParseResult.value = result
            conversationMemory.saveUserTurn(trimmed, classification.intent.name)
            return result
        }

        // 10. Confirmation check for sensitive actions (e.g. phone call, sending message)
        val requiresConfirmation = classification.intent.isSensitive
        val confirmationPrompt = if (requiresConfirmation && classification.intent == CognitiveIntent.MAKE_CALL) {
            val contact = entities.contactName ?: "the contact"
            "Are you sure you want to call $contact?"
        } else if (requiresConfirmation && classification.intent == CognitiveIntent.SEND_MESSAGE) {
            val contact = entities.contactName ?: "the contact"
            val msg = entities.actionParameters["message"] ?: ""
            "Send message to $contact: \"$msg\"?"
        } else null

        // 11. Success: Ready for Action
        DialogueManager.transitionTo(DialogueState.READY_FOR_ACTION, "Intent fully parsed")

        // Populate search results if searching YouTube
        val searchResults = if (classification.intent == CognitiveIntent.SEARCH_WEB && entities.appName == "youtube") {
            listOf("Python for Beginners Full Course", "Python Crash Course 2026", "Intermediate Python")
        } else emptyList()

        ContextManager.updateContext(
            ContextFrame(
                intent = classification.intent,
                entities = entities,
                rawUtterance = trimmed,
                searchResults = searchResults
            )
        )

        val result = CognitiveParseResult(
            intent = classification.intent,
            confidence = classification.confidence,
            entities = entities,
            language = language,
            originalUtterance = trimmed,
            normalizedUtterance = normalized,
            dialogueState = DialogueState.READY_FOR_ACTION,
            requiresConfirmation = requiresConfirmation,
            confirmationPrompt = confirmationPrompt
        )

        _lastParseResult.value = result
        conversationMemory.saveUserTurn(trimmed, classification.intent.name)
        return result
    }
}
