package com.sam.assistant.knowledge.memory

class MemoryRetriever(private val factDao: PersonalFactDao) {

    suspend fun getActiveMemories(): List<PersonalFactEntity> {
        val all = factDao.getAllFactsSync()
        val now = System.currentTimeMillis()
        return all.filter { it.expirationTimestamp == null || it.expirationTimestamp > now }
    }

    suspend fun getMemoriesByCategory(category: MemoryCategory): List<PersonalFactEntity> {
        return getActiveMemories().filter { it.category == category.name }
    }

    suspend fun getMemoriesBySubject(subject: String): List<PersonalFactEntity> {
        val now = System.currentTimeMillis()
        val results = factDao.getFactsBySubject(subject)
        return results.filter { it.expirationTimestamp == null || it.expirationTimestamp > now }
    }

    suspend fun search(query: String): List<PersonalFactEntity> {
        val now = System.currentTimeMillis()
        val results = factDao.searchFacts(query)
        return results.filter { it.expirationTimestamp == null || it.expirationTimestamp > now }
    }

    suspend fun findSpecificFact(subjectKeywords: List<String>, contentKeywords: List<String>): PersonalFactEntity? {
        val active = getActiveMemories()
        return active.firstOrNull { fact ->
            val subMatch = subjectKeywords.isEmpty() || subjectKeywords.any { fact.subject.contains(it, ignoreCase = true) }
            val conMatch = contentKeywords.isEmpty() || contentKeywords.all { fact.content.contains(it, ignoreCase = true) }
            subMatch && conMatch
        }
    }
}
