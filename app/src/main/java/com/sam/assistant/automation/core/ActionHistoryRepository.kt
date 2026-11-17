package com.sam.assistant.automation.core

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.memory.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ActionHistoryRepository(
    private val memoryRepository: MemoryRepository
) {
    companion object {
        private const val TAG = "ActionHistoryRepository"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun logExecution(
        request: ActionRequest,
        status: String,
        details: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val id = memoryRepository.logCommand(
            command = request.rawCommand.ifBlank { request.actionType.name },
            actionType = request.actionType.name,
            status = status,
            details = details
        )
        Log.d(TAG, "Action logged in Room DB [ID: $id, Action: ${request.actionType.name}, Status: $status]")
        id
    }

    fun getAllLogs(): Flow<List<CommandLogEntity>> = memoryRepository.getAllCommandLogs()

    suspend fun getRecentLogs(limit: Int = 20): List<CommandLogEntity> = withContext(Dispatchers.IO) {
        val all = memoryRepository.getAllCommandLogs().first()
        all.take(limit)
    }

    suspend fun exportLogsJson(): String = withContext(Dispatchers.IO) {
        val all = memoryRepository.getAllCommandLogs().first()
        gson.toJson(all)
    }
}
