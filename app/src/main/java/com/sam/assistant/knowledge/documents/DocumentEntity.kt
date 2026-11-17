package com.sam.assistant.knowledge.documents

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_documents",
    indices = [
        Index(value = ["docId"], unique = true),
        Index(value = ["fileName"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val docId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uriString: String,
    val importDate: Long = System.currentTimeMillis(),
    val isScanned: Boolean = false,
    val pageCount: Int = 1,
    val extractedTextLength: Int = 0,
    val summary: String = "",
    val topics: String = "",
    val indexingStatus: String = "INDEXED" // "INDEXED", "SCANNED_LIMITATION", "FAILED"
)

@Entity(
    tableName = "knowledge_sections",
    indices = [
        Index(value = ["docId"]),
        Index(value = ["tokens"])
    ]
)
data class DocumentSectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val docId: String,
    val sectionIndex: Int,
    val title: String,
    val content: String,
    val tokens: String // comma-separated keywords for tokenized indexing
)
