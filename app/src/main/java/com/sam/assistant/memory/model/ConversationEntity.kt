package com.sam.assistant.memory.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_history")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user" or "sam"
    val content: String,
    val intent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

