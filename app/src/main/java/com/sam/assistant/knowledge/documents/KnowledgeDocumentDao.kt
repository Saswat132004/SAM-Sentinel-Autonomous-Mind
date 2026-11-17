package com.sam.assistant.knowledge.documents

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDocumentDao {

    // Document operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("SELECT * FROM knowledge_documents WHERE docId = :docId")
    suspend fun getDocumentByDocId(docId: String): DocumentEntity?

    @Query("SELECT * FROM knowledge_documents ORDER BY importDate DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM knowledge_documents ORDER BY importDate DESC")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>

    // Section operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<DocumentSectionEntity>)

    @Query("SELECT * FROM knowledge_sections WHERE docId = :docId ORDER BY sectionIndex ASC")
    suspend fun getSectionsForDocument(docId: String): List<DocumentSectionEntity>

    @Query("SELECT * FROM knowledge_sections WHERE LOWER(content) LIKE '%' || LOWER(:query) || '%' OR LOWER(title) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchSections(query: String): List<DocumentSectionEntity>

    @Query("DELETE FROM knowledge_sections WHERE docId = :docId")
    suspend fun deleteSectionsForDocument(docId: String): Int

    @Query("DELETE FROM knowledge_documents WHERE docId = :docId")
    suspend fun deleteDocumentByDocId(docId: String): Int

    @Transaction
    suspend fun deleteDocumentAndSections(docId: String): Boolean {
        deleteSectionsForDocument(docId)
        val count = deleteDocumentByDocId(docId)
        return count > 0
    }

    @Query("DELETE FROM knowledge_documents")
    suspend fun clearAllDocuments()

    @Query("DELETE FROM knowledge_sections")
    suspend fun clearAllSections()

    @Transaction
    suspend fun clearAllKnowledge() {
        clearAllSections()
        clearAllDocuments()
    }
}
