package com.sam.assistant.agent.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AgentTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AgentTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<AgentTaskEntity>)

    @Update
    suspend fun updateTask(task: AgentTaskEntity)

    @Query("SELECT * FROM agent_tasks WHERE goalId = :goalId ORDER BY id ASC")
    suspend fun getTasksForGoal(goalId: String): List<AgentTaskEntity>

    @Query("SELECT * FROM agent_tasks ORDER BY timestamp DESC")
    suspend fun getAllTasks(): List<AgentTaskEntity>

    @Query("DELETE FROM agent_tasks WHERE goalId = :goalId")
    suspend fun deleteTasksForGoal(goalId: String)

    @Query("DELETE FROM agent_tasks")
    suspend fun clearAllTasks()
}
