package com.sam.assistant.agent.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: String,
    val taskId: String,
    val description: String,
    val toolName: String,
    val status: String,
    val riskLevel: String,
    val dependencies: String, // Comma-delimited list of dependency task IDs
    val executionEvidence: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
