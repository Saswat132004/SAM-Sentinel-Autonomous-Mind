package com.sam.assistant.ai.cognitive.memory

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sam.assistant.ai.cognitive.context.ContextManager
import com.sam.assistant.memory.model.ConversationEntity
import com.sam.assistant.memory.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ConversationMemory(
    private val memoryRepository: MemoryRepository
) {
    companion object {
        private const val TAG = "ConversationMemory"
        // Raw audio storage is strictly forbidden and disabled
        const val IS_RAW_AUDIO_STORAGE_ENABLED = false
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Saves user message turn with metadata. Does not store raw audio.
     */
    suspend fun saveUserTurn(
        utterance: String,
        intent: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val id = memoryRepository.saveConversation(
            role = "user",
            content = utterance,
            intent = intent
        )
        Log.d(TAG, "User turn saved in Room DB [ID: $id, Intent: $intent]")
        id
    }

    /**
     * Saves SAM's spoken and visual response.
     */
    suspend fun saveAssistantTurn(
        response: String,
        intent: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val id = memoryRepository.saveConversation(
            role = "sam",
            content = response,
            intent = intent
        )
        Log.d(TAG, "Assistant turn saved in Room DB [ID: $id]")
        id
    }

    /**
     * Retrieves recent conversation turns.
     */
    suspend fun getRecentTurns(limit: Int = 10): List<ConversationEntity> = withContext(Dispatchers.IO) {
        memoryRepository.getRecentConversation(limit)
    }

    /**
     * Edits a previously saved conversation turn.
     */
    suspend fun editTurn(id: Long, newContent: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Editing turn $id with new content")
        // Since Room DAO provides delete and insert, we update via repository
        val current = memoryRepository.getRecentConversation(50).find { it.id == id }
        if (current != null) {
            memoryRepository.deleteConversation(id)
            memoryRepository.saveConversation(
                role = current.role,
                content = newContent,
                intent = current.intent
            )
        }
    }

    /**
     * Deletes a specific conversation turn by ID.
     */
    suspend fun deleteTurn(id: Long) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Deleting turn $id")
        memoryRepository.deleteConversation(id)
    }

    /**
     * Clears all conversation history.
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing all conversation history")
        memoryRepository.clearConversations()
        ContextManager.clearAll()
    }

    /**
     * Searches conversation history for a given keyword query.
     */
    suspend fun searchHistory(query: String): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val all = memoryRepository.getRecentConversation(100)
        all.filter { it.content.contains(query, ignoreCase = true) }
    }

    /**
     * Exports conversation history to a structured JSON string.
     */
    suspend fun exportHistoryAsJson(): String = withContext(Dispatchers.IO) {
        val history = memoryRepository.getRecentConversation(200)
        gson.toJson(history)
    }

    /**
     * Generates a conversation summary string.
     */
    suspend fun generateConversationSummary(): String = withContext(Dispatchers.IO) {
        val recent = memoryRepository.getRecentConversation(10)
        if (recent.isEmpty()) return@withContext "No recent conversation history recorded."

        val userQueries = recent.filter { it.role == "user" }.map { it.content }
        val topics = userQueries.take(4).joinToString("; ")
        "Recent conversation involved ${recent.size} turns. User explored: $topics."
    }

    /**
     * Stores an explicitly approved persistent preference in long-term memory.
     * Note: Daily conversations are never automatically converted into permanent memory.
     */
    suspend fun storeApprovedPreference(key: String, value: String): Long = withContext(Dispatchers.IO) {
        Log.d(TAG, "Storing approved user preference: $key = $value")
        ContextManager.setApprovedPreference(key, value)
        memoryRepository.saveMemory(
            key = key,
            value = value,
            category = "Preference",
            tags = "approved_preference"
        )
    }

    suspend fun getApprovedPreferences(): Map<String, String> = withContext(Dispatchers.IO) {
        val memories = memoryRepository.getAllMemories().first()
        memories.filter { it.category == "Preference" }.associate { it.key to it.value }
    }
}
