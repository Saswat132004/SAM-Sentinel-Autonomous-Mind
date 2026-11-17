package com.sam.assistant.knowledge.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "personal_facts",
    indices = [
        Index(value = ["factId"], unique = true),
        Index(value = ["category"]),
        Index(value = ["subject"])
    ]
)
data class PersonalFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val factId: String,
    val category: String, // String representation of MemoryCategory
    val subject: String,
    val content: String,
    val sourceReference: String,
    val isUserApproved: Boolean = true,
    val confidence: Float = 1.0f,
    val expirationTimestamp: Long? = null,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
