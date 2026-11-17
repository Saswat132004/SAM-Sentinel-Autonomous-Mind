package com.sam.assistant.memory.database

import androidx.room.*
import com.sam.assistant.memory.model.CommandLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<CommandLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLogEntity): Long

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}

