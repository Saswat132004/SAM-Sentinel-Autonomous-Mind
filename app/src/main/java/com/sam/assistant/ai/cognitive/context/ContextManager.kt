package com.sam.assistant.ai.cognitive.context

import android.util.Log
import com.sam.assistant.ai.cognitive.models.CognitiveEntities
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.normalizer.LanguageNormalizer

data class ContextFrame(
    val intent: CognitiveIntent,
    val entities: CognitiveEntities,
    val rawUtterance: String,
    val searchResults: List<String> = emptyList(),
    val candidateContacts: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

object ContextManager {

    private const val TAG = "ContextManager"
    private const val CONTEXT_EXPIRATION_MS = 300000L // 5 minutes TTL

    private var activeFrame: ContextFrame? = null
    private val frameHistory = mutableListOf<ContextFrame>()
    private val approvedPreferences = mutableMapOf<String, String>()

    @Synchronized
    fun updateContext(frame: ContextFrame) {
        checkExpiration()
        activeFrame = frame
        frameHistory.add(frame)
        if (frameHistory.size > 20) {
            frameHistory.removeAt(0)
        }
        Log.d(TAG, "Context updated: Intent=${frame.intent}, App=${frame.entities.appName}, Contact=${frame.entities.contactName}")
    }

    @Synchronized
    fun getActiveFrame(): ContextFrame? {
        checkExpiration()
        return activeFrame
    }

    @Synchronized
    fun isContextValid(): Boolean {
        checkExpiration()
        return activeFrame != null
    }

    @Synchronized
    fun clearActiveContext() {
        activeFrame = null
    }

    @Synchronized
    fun clearAll() {
        activeFrame = null
        frameHistory.clear()
    }

    /**
     * Resolves ordinal / pronoun references against the active context.
     * E.g. User says "Play the second one" after "Search Python tutorials on YouTube".
     */
    @Synchronized
    fun resolveReference(input: String, ordinal: Int?): Pair<CognitiveIntent, CognitiveEntities>? {
        checkExpiration()
        val current = activeFrame ?: return null

        val lower = input.lowercase()

        // 1. Ordinal resolution ("the second one", "doosra")
        if (ordinal != null) {
            // Case A: Disambiguating candidate contacts
            if (current.candidateContacts.isNotEmpty()) {
                val index = if (ordinal == -1) current.candidateContacts.size - 1 else ordinal - 1
                if (index in current.candidateContacts.indices) {
                    val resolvedContact = current.candidateContacts[index]
                    val updatedEntities = current.entities.copy(
                        contactName = resolvedContact,
                        ordinal = ordinal
                    )
                    return Pair(current.intent, updatedEntities)
                }
            }

            // Case B: Search results (e.g. YouTube search)
            if (current.intent == CognitiveIntent.SEARCH_WEB || current.entities.appName == "youtube") {
                val updatedEntities = current.entities.copy(
                    ordinal = ordinal,
                    mediaTitle = if (current.searchResults.isNotEmpty()) {
                        val idx = if (ordinal == -1) current.searchResults.size - 1 else ordinal - 1
                        current.searchResults.getOrNull(idx) ?: "item #$ordinal"
                    } else {
                        "item #$ordinal"
                    }
                )
                return Pair(CognitiveIntent.PLAY_MEDIA, updatedEntities)
            }
        }

        // 2. Pronoun resolution ("open it", "call him", "message her")
        if (lower.contains(Regex("\\b(?:open\\s+it|launch\\s+it|use\\s+it)\\b"))) {
            if (current.entities.appName != null) {
                return Pair(CognitiveIntent.OPEN_APP, current.entities)
            }
        }
        if (lower.contains(Regex("\\b(?:call\\s+him|call\\s+her|call\\s+them)\\b"))) {
            if (current.entities.contactName != null) {
                return Pair(CognitiveIntent.MAKE_CALL, current.entities)
            }
        }

        return null
    }

    /**
     * Resolves user corrections on an active command.
     * E.g. User: "Set an alarm for 6 AM." -> User: "Actually, make it 7 AM."
     */
    @Synchronized
    fun resolveCorrection(input: String): Pair<CognitiveIntent, CognitiveEntities>? {
        checkExpiration()
        val current = activeFrame ?: return null
        val lower = input.lowercase()

        val isCorrection = lower.startsWith("actually") || lower.startsWith("nahi") ||
                lower.startsWith("change to") || lower.contains("make it") || lower.contains("badal do")

        if (!isCorrection) return null

        val newTime = LanguageNormalizer.normalizeTime(input)
        val newDate = LanguageNormalizer.normalizeRelativeDate(input)

        if (current.intent == CognitiveIntent.SET_ALARM || current.intent == CognitiveIntent.CREATE_REMINDER || current.intent == CognitiveIntent.SET_TIMER) {
            val updatedEntities = current.entities.copy(
                time = newTime ?: current.entities.time,
                date = newDate ?: current.entities.date
            )
            Log.d(TAG, "Correction resolved for ${current.intent}: time changed to ${updatedEntities.time}")
            return Pair(current.intent, updatedEntities)
        }

        return null
    }

    /**
     * Checks if short-term conversation context has expired (5-minute inactivity window).
     */
    private fun checkExpiration() {
        val frame = activeFrame ?: return
        val elapsed = System.currentTimeMillis() - frame.timestampMs
        if (elapsed > CONTEXT_EXPIRATION_MS) {
            Log.d(TAG, "Context expired after $elapsed ms of inactivity.")
            activeFrame = null
        }
    }

    /**
     * Approved User Preferences (explicitly saved long-term memory)
     */
    fun setApprovedPreference(key: String, value: String) {
        approvedPreferences[key] = value
    }

    fun getApprovedPreference(key: String): String? = approvedPreferences[key]

    fun getAllApprovedPreferences(): Map<String, String> = approvedPreferences.toMap()

    /**
     * Summarizes recent multi-turn context
     */
    @Synchronized
    fun summarizeContext(): String {
        if (frameHistory.isEmpty()) return "No conversation history."
        return frameHistory.takeLast(5).joinToString(" -> ") {
            "${it.intent.name}(app=${it.entities.appName}, contact=${it.entities.contactName})"
        }
    }
}
