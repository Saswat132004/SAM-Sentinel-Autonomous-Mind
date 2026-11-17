package com.sam.assistant.knowledge.memory

class MemoryUpdater(private val factDao: PersonalFactDao) {

    suspend fun updateFactContent(factId: String, newContent: String): Boolean {
        val existing = factDao.getFactByFactId(factId) ?: return false
        val updated = existing.copy(
            content = newContent,
            updatedAt = System.currentTimeMillis()
        )
        factDao.updateFact(updated)
        return true
    }

    suspend fun updateFactCategory(factId: String, newCategory: MemoryCategory): Boolean {
        val existing = factDao.getFactByFactId(factId) ?: return false
        val updated = existing.copy(
            category = newCategory.name,
            updatedAt = System.currentTimeMillis()
        )
        factDao.updateFact(updated)
        return true
    }
}
