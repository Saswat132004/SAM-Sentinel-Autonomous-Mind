package com.sam.assistant.knowledge.memory

class MemoryDeletionManager(private val factDao: PersonalFactDao) {

    suspend fun deleteByFactId(factId: String): Boolean {
        val deletedCount = factDao.deleteByFactId(factId)
        return deletedCount > 0
    }

    suspend fun deleteBySubject(subject: String): Int {
        return factDao.deleteBySubject(subject)
    }

    suspend fun deleteAll(): Boolean {
        factDao.clearAllFacts()
        return true
    }
}
