package com.sam.assistant.memory.database

import androidx.room.*
import com.sam.assistant.memory.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_history ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Query("DELETE FROM conversation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_history")
    suspend fun clearAll()
}

