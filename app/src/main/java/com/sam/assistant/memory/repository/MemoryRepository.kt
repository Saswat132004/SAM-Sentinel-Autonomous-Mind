package com.sam.assistant.memory.repository

import com.google.gson.GsonBuilder
import com.sam.assistant.core.security.KeystoreManager
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.memory.database.SamDatabase
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.memory.model.ConversationEntity
import com.sam.assistant.memory.model.MemoryEntity
import com.sam.assistant.memory.model.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

open class MemoryRepository(private val database: SamDatabase? = null) {

    private val memoryDao by lazy { database?.memoryDao() ?: error("MemoryDao not available") }
    private val conversationDao by lazy { database?.conversationDao() ?: error("ConversationDao not available") }
    private val commandLogDao by lazy { database?.commandLogDao() ?: error("CommandLogDao not available") }
    private val reminderDao by lazy { database?.reminderDao() ?: error("ReminderDao not available") }

    // --- Personal Memory ---
    fun getAllMemories(): Flow<List<MemoryEntity>> {
        return memoryDao.getAllMemories().map { list ->
            list.map { memory ->
                if (memory.isEncrypted) {
                    memory.copy(value = KeystoreManager.decrypt(memory.value))
                } else {
                    memory
                }
            }
        }
    }

    suspend fun saveMemory(
        key: String,
        value: String,
        category: String = "General",
        tags: String = "",
        encrypt: Boolean = true
    ): Long {
        LockStateManager.assertUnlocked()
        val storedValue = if (encrypt) KeystoreManager.encrypt(value) else value
        val existing = memoryDao.findByKey(key)
        val entity = if (existing != null) {
            existing.copy(
                value = storedValue,
                category = category,
                tags = tags,
                isEncrypted = encrypt,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            MemoryEntity(
                key = key,
                value = storedValue,
                category = category,
                isEncrypted = encrypt,
                tags = tags
            )
        }
        return memoryDao.insertMemory(entity)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        LockStateManager.assertUnlocked()
        val rawResults = memoryDao.searchMemories(query)
        return rawResults.map { memory ->
            if (memory.isEncrypted) {
                memory.copy(value = KeystoreManager.decrypt(memory.value))
            } else {
                memory
            }
        }
    }

    suspend fun deleteMemory(id: Long) {
        LockStateManager.assertUnlocked()
        memoryDao.deleteById(id)
    }

    suspend fun deleteMemoryByKey(key: String): Int {
        LockStateManager.assertUnlocked()
        return memoryDao.deleteByKey(key)
    }

    // --- Conversation History ---
    fun getAllConversations(): Flow<List<ConversationEntity>> = conversationDao.getAllMessages()

    suspend fun getRecentConversation(limit: Int = 10): List<ConversationEntity> {
        return conversationDao.getRecentMessages(limit)
    }

    suspend fun saveConversation(role: String, content: String, intent: String? = null): Long {
        return conversationDao.insertMessage(
            ConversationEntity(
                role = role,
                content = content,
                intent = intent
            )
        )
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
    }

    suspend fun clearConversations() {
        conversationDao.clearAll()
    }

    // --- Command Log ---
    fun getAllCommandLogs(): Flow<List<CommandLogEntity>> = commandLogDao.getAllLogs()

    suspend fun logCommand(command: String, actionType: String, status: String, details: String = ""): Long {
        return commandLogDao.insertLog(
            CommandLogEntity(
                command = command,
                actionType = actionType,
                status = status,
                details = details
            )
        )
    }

    // --- Reminders ---
    open fun getActiveReminders(): Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()

    open suspend fun saveReminder(title: String, triggerTimeMs: Long, recurrence: String = "NONE"): Long {
        return reminderDao.insertReminder(
            ReminderEntity(
                title = title,
                triggerTimeMs = triggerTimeMs,
                recurrence = recurrence
            )
        )
    }

    suspend fun completeReminder(id: Long) {
        reminderDao.markCompleted(id)
    }

    suspend fun deleteReminder(id: Long) {
        val rem = reminderDao.getReminderById(id)
        if (rem != null) reminderDao.deleteReminder(rem)
    }

    // --- Privacy & Security Center Operations ---
    suspend fun exportAllDataToJson(): String {
        LockStateManager.assertUnlocked()
        val memories = getAllMemories().first()
        val conversations = conversationDao.getAllMessages().first()
        val commands = commandLogDao.getAllLogs().first()
        val reminders = reminderDao.getAllReminders().first()

        val exportData = mapOf(
            "app" to "SAM - Secure Autonomous Mobile Assistant",
            "version" to "1.0.0-PROD",
            "exportTimestamp" to System.currentTimeMillis(),
            "personalMemories" to memories,
            "conversations" to conversations,
            "commandLogs" to commands,
            "reminders" to reminders
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(exportData)
    }

    suspend fun deleteAllUserData() {
        LockStateManager.assertUnlocked()
        memoryDao.clearAll()
        conversationDao.clearAll()
        commandLogDao.clearAll()
        reminderDao.clearAll()
    }
}

