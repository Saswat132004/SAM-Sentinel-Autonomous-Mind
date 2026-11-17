package com.sam.assistant.memory.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String, // Encrypted if isEncrypted == true
    val category: String = "General", // e.g., Projects, Preferences, Work, Personal
    val isEncrypted: Boolean = true,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

