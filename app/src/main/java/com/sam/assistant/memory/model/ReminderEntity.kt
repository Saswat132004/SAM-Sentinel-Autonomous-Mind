package com.sam.assistant.memory.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerTimeMs: Long,
    val isCompleted: Boolean = false,
    val recurrence: String = "NONE", // NONE, DAILY, WEEKLY
    val createdAt: Long = System.currentTimeMillis()
)

