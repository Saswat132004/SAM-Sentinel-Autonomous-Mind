package com.sam.assistant.knowledge.retrieval

import com.sam.assistant.knowledge.documents.DocumentSectionEntity
import com.sam.assistant.knowledge.documents.KnowledgeDocumentDao
import com.sam.assistant.knowledge.memory.PersonalFactDao
import com.sam.assistant.knowledge.memory.PersonalFactEntity
import kotlin.math.ln

data class RetrievalResultItem(
    val title: String,
    val content: String,
    val sourceCitation: String,
    val score: Float,
    val category: String, // "MEMORY_FACT", "DOCUMENT_SECTION"
    val timestamp: Long = System.currentTimeMillis()
)

class HybridRetrievalEngine(
    private val factDao: PersonalFactDao,
    private val documentDao: KnowledgeDocumentDao
) {

    /**
     * Executes hybrid retrieval across long-term personal facts and indexed document sections.
     */
    suspend fun retrieve(
        query: String,
        activeTopic: String? = null,
        limit: Int = 5
    ): List<RetrievalResultItem> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val results = mutableListOf<RetrievalResultItem>()

        // 1. Structured & Full-Text Search on Personal Facts
        val facts = factDao.getAllFactsSync()
        for (fact in facts) {
            val factTokens = tokenize("${fact.subject} ${fact.content} ${fact.tags}")
            val bm25Score = calculateBM25Score(queryTokens, factTokens, fact.content.length)
            val semanticScore = calculateSemanticOverlap(queryTokens, factTokens)
            val topicScore = if (activeTopic != null && (fact.subject.contains(activeTopic, ignoreCase = true) || fact.content.contains(activeTopic, ignoreCase = true))) 1.0f else 0.0f
            val recencyScore = calculateRecencyScore(fact.updatedAt)

            val totalScore = (bm25Score * 0.4f) + (semanticScore * 0.35f) + (topicScore * 0.15f) + (recencyScore * 0.10f)

            if (totalScore > 0.15f) {
                results.add(
                    RetrievalResultItem(
                        title = fact.subject,
                        content = fact.content,
                        sourceCitation = "Personal Memory [Fact: ${fact.factId}, Cat: ${fact.category}]",
                        score = totalScore,
                        category = "MEMORY_FACT",
                        timestamp = fact.updatedAt
                    )
                )
            }
        }

        // 2. Full-Text Search on Ingested Document Sections
        val sections = documentDao.searchSections(query.trim())
        for (section in sections) {
            val sectionTokens = tokenize("${section.title} ${section.content} ${section.tokens}")
            val bm25Score = calculateBM25Score(queryTokens, sectionTokens, section.content.length)
            val semanticScore = calculateSemanticOverlap(queryTokens, sectionTokens)
            val topicScore = if (activeTopic != null && section.content.contains(activeTopic, ignoreCase = true)) 1.0f else 0.0f

            val totalScore = (bm25Score * 0.45f) + (semanticScore * 0.35f) + (topicScore * 0.20f)

            if (totalScore > 0.12f) {
                results.add(
                    RetrievalResultItem(
                        title = section.title,
                        content = section.content,
                        sourceCitation = "Knowledge Base [Doc: ${section.docId}, Sec: ${section.sectionIndex}]",
                        score = totalScore,
                        category = "DOCUMENT_SECTION",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }.take(limit)
    }

    private fun tokenize(text: String): List<String> {
        val stopWords = setOf("the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "with", "about", "what", "how", "why")
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
    }

    private fun calculateBM25Score(queryTokens: List<String>, docTokens: List<String>, docLength: Int): Float {
        var score = 0f
        val docTokenCounts = docTokens.groupingBy { it }.eachCount()
        val avgDocLength = 500f
        val k1 = 1.2f
        val b = 0.75f

        for (qToken in queryTokens) {
            val tf = docTokenCounts[qToken] ?: 0
            if (tf > 0) {
                val idf = ln(1.0 + (100.0 / (1.0 + tf))).toFloat()
                val numerator = tf * (k1 + 1f)
                val denominator = tf + k1 * (1f - b + b * (docLength / avgDocLength))
                score += idf * (numerator / denominator)
            }
        }
        return (score / (queryTokens.size * 2f)).coerceIn(0f, 1f)
    }

    private fun calculateSemanticOverlap(queryTokens: List<String>, docTokens: List<String>): Float {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) return 0f
        val docSet = docTokens.toSet()
        val matchCount = queryTokens.count { docSet.contains(it) }
        return matchCount.toFloat() / queryTokens.size.toFloat()
    }

    private fun calculateRecencyScore(timestamp: Long): Float {
        val ageMs = System.currentTimeMillis() - timestamp
        val oneWeekMs = 7 * 24 * 3600 * 1000L
        return (1.0f - (ageMs.toFloat() / oneWeekMs.toFloat())).coerceIn(0.1f, 1.0f)
    }
}
