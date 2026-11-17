package com.sam.assistant.memory.database

import androidx.room.*
import com.sam.assistant.memory.model.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM personal_memory ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM personal_memory WHERE id = :id")
    suspend fun getMemoryById(id: Long): MemoryEntity?

    @Query("SELECT * FROM personal_memory WHERE category = :category ORDER BY updatedAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM personal_memory WHERE key LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM personal_memory WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM personal_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM personal_memory WHERE key LIKE '%' || :keyQuery || '%'")
    suspend fun deleteByKey(keyQuery: String): Int

    @Query("DELETE FROM personal_memory")
    suspend fun clearAll()
}

