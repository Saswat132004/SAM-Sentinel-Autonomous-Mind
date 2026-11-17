package com.sam.assistant.ai.cognitive.models

import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import com.sam.assistant.voice.language.SpokenLanguage

/**
 * Stage 3: Master Intent Catalogue
 */
enum class CognitiveIntent(val displayName: String, val isSensitive: Boolean) {
    OPEN_APP("Open Application", false),
    CLOSE_APP("Close Application", false),
    SEARCH_WEB("Search Web", false),
    PLAY_MEDIA("Play Media", false),
    MAKE_CALL("Make Phone Call", true),
    SEND_MESSAGE("Send Message", true),
    CREATE_REMINDER("Create Reminder", false),
    SET_ALARM("Set Alarm", false),
    SET_TIMER("Set Timer", false),
    GET_DEVICE_INFO("Get Device Info", false),
    CONTROL_DEVICE("Control Device Setting", false),
    SCAN_WIFI("Scan Wi-Fi", false),
    ANALYSE_NETWORK("Analyse Network", false),
    READ_DOCUMENT("Read Document", false),
    SUMMARISE_DOCUMENT("Summarise Document", false),
    EXPLAIN_CONCEPT("Explain Concept", false),
    GENERAL_CONVERSATION("General Conversation", false),
    FOLLOW_UP_COMMAND("Follow-up Command", false),
    UNKNOWN_INTENT("Unknown Intent", false)
}

/**
 * Stage 3: Dialogue States
 */
enum class DialogueState {
    IDLE,
    UNDERSTANDING,
    NEEDS_CLARIFICATION,
    WAITING_FOR_RESPONSE,
    RESOLVING_CONTEXT,
    READY_FOR_ACTION,
    COMPLETED,
    FAILED
}

/**
 * Stage 3: Strongly-typed entity model
 */
data class CognitiveEntities(
    val appName: String? = null,
    val contactName: String? = null,
    val location: String? = null,
    val date: String? = null,
    val time: String? = null,
    val searchQuery: String? = null,
    val documentName: String? = null,
    val mediaTitle: String? = null,
    val subject: String? = null,
    val userPreferences: Map<String, String> = emptyMap(),
    val number: Int? = null,
    val ordinal: Int? = null, // 1 = first / pehla, 2 = second / doosra, etc.
    val actionParameters: Map<String, String> = emptyMap()
)

/**
 * Stage 3: Intent classification intermediate result with confidence scoring
 */
data class IntentClassificationResult(
    val intent: CognitiveIntent,
    val confidence: Float, // 0.0f .. 1.0f
    val rawUtterance: String,
    val normalizedUtterance: String,
    val isAmbiguous: Boolean = false,
    val clarificationOptions: List<String> = emptyList(),
    val matchedPattern: String? = null
)

/**
 * Stage 3: Structured multi-step reasoning plan proposal
 */
data class StructuredPlanProposal(
    val objective: String,
    val subtasks: List<String>,
    val requiredInfo: List<String>,
    val missingInfo: List<String>,
    val conciseUserExplanation: String,
    val executableActions: List<ActionRequest>
)

/**
 * Stage 3: Complete cognitive parsing and understanding output
 */
data class CognitiveParseResult(
    val intent: CognitiveIntent,
    val confidence: Float,
    val entities: CognitiveEntities,
    val language: SpokenLanguage,
    val originalUtterance: String,
    val normalizedUtterance: String,
    val dialogueState: DialogueState,
    val isClarificationNeeded: Boolean = false,
    val clarificationPrompt: String? = null,
    val clarificationOptions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val reasoningProposal: StructuredPlanProposal? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Maps cognitive intent and entities into an executable ActionRequest
     */
    fun toActionRequest(): ActionRequest {
        val actionType = when (intent) {
            CognitiveIntent.OPEN_APP -> ActionType.LAUNCH_APP
            CognitiveIntent.CLOSE_APP -> ActionType.LAUNCH_APP
            CognitiveIntent.SEARCH_WEB -> ActionType.SEARCH_WEB
            CognitiveIntent.PLAY_MEDIA -> ActionType.PLAY_MEDIA
            CognitiveIntent.MAKE_CALL -> ActionType.MAKE_CALL
            CognitiveIntent.SEND_MESSAGE -> {
                if (entities.appName?.contains("whatsapp", ignoreCase = true) == true) {
                    ActionType.SEND_WHATSAPP
                } else {
                    ActionType.SEND_SMS
                }
            }
            CognitiveIntent.CREATE_REMINDER -> ActionType.CREATE_REMINDER
            CognitiveIntent.SET_ALARM -> ActionType.SET_ALARM
            CognitiveIntent.SET_TIMER -> ActionType.CREATE_REMINDER
            CognitiveIntent.GET_DEVICE_INFO -> {
                if (entities.actionParameters["type"] == "battery") {
                    ActionType.READ_BATTERY
                } else {
                    ActionType.READ_TIME_DATE
                }
            }
            CognitiveIntent.CONTROL_DEVICE -> {
                val param = entities.actionParameters["target"] ?: ""
                when {
                    param.contains("torch") || param.contains("flashlight") -> ActionType.TOGGLE_FLASHLIGHT
                    param.contains("volume") -> ActionType.ADJUST_VOLUME
                    else -> ActionType.OPEN_SETTINGS
                }
            }
            CognitiveIntent.SCAN_WIFI -> ActionType.OPEN_SETTINGS
            CognitiveIntent.ANALYSE_NETWORK -> ActionType.OPEN_SETTINGS
            CognitiveIntent.READ_DOCUMENT -> ActionType.CONVERSATIONAL_RESPONSE
            CognitiveIntent.SUMMARISE_DOCUMENT -> ActionType.CONVERSATIONAL_RESPONSE
            CognitiveIntent.EXPLAIN_CONCEPT -> ActionType.CONVERSATIONAL_RESPONSE
            CognitiveIntent.GENERAL_CONVERSATION -> ActionType.CONVERSATIONAL_RESPONSE
            CognitiveIntent.FOLLOW_UP_COMMAND -> ActionType.CONVERSATIONAL_RESPONSE
            CognitiveIntent.UNKNOWN_INTENT -> ActionType.UNKNOWN
        }

        val target = entities.appName ?: entities.contactName ?: entities.actionParameters["target"] ?: ""
        val payload = entities.searchQuery ?: entities.subject ?: entities.actionParameters["message"] ?: entities.mediaTitle ?: ""

        val extra = mutableMapOf<String, String>().apply {
            putAll(entities.actionParameters)
            entities.date?.let { put("date", it) }
            entities.time?.let { put("time", it) }
            entities.number?.let { put("number", it.toString()) }
            entities.ordinal?.let { put("ordinal", it.toString()) }
            if (intent == CognitiveIntent.SET_ALARM && entities.time != null) {
                val parts = entities.time.split(":")
                if (parts.isNotEmpty()) put("hour", parts[0])
                if (parts.size > 1) put("minute", parts[1])
            }
        }

        return ActionRequest(
            actionType = actionType,
            target = target,
            payload = payload,
            extraParams = extra,
            rawCommand = originalUtterance,
            requiresConfirmation = requiresConfirmation || actionType.isSensitive,
            confirmationPrompt = confirmationPrompt ?: "Are you sure you want to proceed?"
        )
    }
}
