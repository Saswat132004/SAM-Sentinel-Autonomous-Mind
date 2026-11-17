package com.sam.assistant.knowledge.memory

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SaveMemoryResult {
    data class Success(val fact: PersonalFactEntity, val userMessage: String) : SaveMemoryResult()
    data class RequiresApproval(val prompt: String, val candidateFact: ExtractedMemory) : SaveMemoryResult()
    data class Denied(val reason: String) : SaveMemoryResult()
}

class PersonalMemoryEngine(
    val factDao: PersonalFactDao,
    val classifier: MemoryClassifier = MemoryClassifier(),
    val extractor: MemoryExtractor = MemoryExtractor(classifier),
    val privacyController: MemoryPrivacyController = MemoryPrivacyController()
) {
    val retriever: MemoryRetriever = MemoryRetriever(factDao)
    val updater: MemoryUpdater = MemoryUpdater(factDao)
    val deletionManager: MemoryDeletionManager = MemoryDeletionManager(factDao)
    val contextBuilder: MemoryContextBuilder = MemoryContextBuilder(retriever)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun saveApprovedMemory(
        statement: String,
        isExplicitRequest: Boolean = true,
        isUserApproved: Boolean = true,
        sourceReference: String = "User Command"
    ): SaveMemoryResult {
        val extracted = extractor.extract(statement)
            ?: return SaveMemoryResult.Denied("Could not extract meaningful information from statement.")

        val privacyCheck = privacyController.canPersistMemory(
            content = extracted.content,
            isExplicitlyRequested = isExplicitRequest,
            isUserApproved = isUserApproved
        )

        when (privacyCheck) {
            is PrivacyCheckResult.Denied -> return SaveMemoryResult.Denied(privacyCheck.reason)
            is PrivacyCheckResult.RequiresApproval -> return SaveMemoryResult.RequiresApproval(privacyCheck.prompt, extracted)
            is PrivacyCheckResult.Allowed -> {
                val entity = PersonalFactEntity(
                    factId = extracted.factId,
                    category = extracted.category.name,
                    subject = extracted.subject,
                    content = extracted.content,
                    sourceReference = sourceReference,
                    isUserApproved = true,
                    confidence = extracted.confidence,
                    tags = extracted.tags.joinToString(",")
                )
                factDao.insertFact(entity)
                return SaveMemoryResult.Success(
                    fact = entity,
                    userMessage = "I have remembered that ${extracted.content} (Categorized under ${extracted.category.name})."
                )
            }
        }
    }

    suspend fun updateMemory(factId: String, newContent: String): Boolean {
        return updater.updateFactContent(factId, newContent)
    }

    suspend fun deleteMemory(factId: String): Boolean {
        return deletionManager.deleteByFactId(factId)
    }

    suspend fun deleteMemoryBySubject(subject: String): Int {
        return deletionManager.deleteBySubject(subject)
    }

    suspend fun deleteAllMemories(): Boolean {
        return deletionManager.deleteAll()
    }

    suspend fun searchMemories(query: String): List<PersonalFactEntity> {
        return retriever.search(query)
    }

    suspend fun getMemoriesBySubject(subject: String): List<PersonalFactEntity> {
        return retriever.getMemoriesBySubject(subject)
    }

    suspend fun getMemoriesByCategory(category: MemoryCategory): List<PersonalFactEntity> {
        return retriever.getMemoriesByCategory(category)
    }

    fun getAllMemoriesFlow(): Flow<List<PersonalFactEntity>> {
        return factDao.getAllFacts()
    }

    suspend fun getAllMemoriesSync(): List<PersonalFactEntity> {
        return retriever.getActiveMemories()
    }

    suspend fun exportAsJson(): String {
        val memories = getAllMemoriesSync()
        return gson.toJson(memories)
    }

    suspend fun exportAsText(): String {
        val memories = getAllMemoriesSync()
        val sb = StringBuilder("=== SAM PERSONAL INTELLIGENCE MEMORY EXPORT ===\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Total Facts: ${memories.size}\n\n")

        MemoryCategory.values().forEach { cat ->
            val catMemories = memories.filter { it.category == cat.name }
            if (catMemories.isNotEmpty()) {
                sb.append("--- CATEGORY: ${cat.name} (${catMemories.size}) ---\n")
                catMemories.forEach { fact ->
                    sb.append("• [${fact.subject}] ${fact.content}\n")
                    sb.append("  ID: ${fact.factId} | Source: ${fact.sourceReference}\n")
                    sb.append("  Saved: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(fact.createdAt))}\n\n")
                }
            }
        }
        return sb.toString()
    }
}
