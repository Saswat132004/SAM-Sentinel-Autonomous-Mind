package com.sam.assistant.memory.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val actionType: String,
    val status: String, // "SUCCESS", "FAILED", "CONFIRMED", "CANCELLED", "PERMISSION_DENIED"
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

