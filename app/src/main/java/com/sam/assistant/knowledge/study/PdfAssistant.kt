package com.sam.assistant.knowledge.study

import com.sam.assistant.knowledge.documents.KnowledgeDocumentDao

data class DocumentQAResult(
    val answer: String,
    val sourceDocument: String,
    val sourceSection: String,
    val isVerifiedFromEvidence: Boolean
)

data class DocumentRevisionNotes(
    val documentTitle: String,
    val pageCount: Int,
    val summary: String,
    val keyConcepts: List<String>,
    val revisionPoints: List<String>
)

class PdfAssistant(private val documentDao: KnowledgeDocumentDao) {

    /**
     * Answers a user query strictly using verified evidence from ingested document sections.
     */
    suspend fun answerQuestion(docId: String, query: String): DocumentQAResult {
        val doc = documentDao.getDocumentByDocId(docId)
            ?: return DocumentQAResult(
                answer = "Document with ID $docId was not found in the local knowledge library.",
                sourceDocument = "Unknown",
                sourceSection = "None",
                isVerifiedFromEvidence = false
            )

        if (doc.isScanned) {
            return DocumentQAResult(
                answer = "Cannot answer from \"${doc.fileName}\": Scanned image-only PDF detected. Text could not be extracted without external OCR.",
                sourceDocument = doc.fileName,
                sourceSection = "Scanned",
                isVerifiedFromEvidence = false
            )
        }

        val sections = documentDao.getSectionsForDocument(docId)
        if (sections.isEmpty()) {
            return DocumentQAResult(
                answer = "No readable text sections available for document \"${doc.fileName}\".",
                sourceDocument = doc.fileName,
                sourceSection = "None",
                isVerifiedFromEvidence = false
            )
        }

        val queryTerms = query.lowercase().split(" ").filter { it.length > 2 }
        val matchingSection = sections.maxByOrNull { sec ->
            val contentLower = sec.content.lowercase()
            queryTerms.count { contentLower.contains(it) }
        }

        if (matchingSection != null && queryTerms.any { matchingSection.content.contains(it, ignoreCase = true) }) {
            return DocumentQAResult(
                answer = "According to \"${doc.fileName}\" (${matchingSection.title}):\n\n${matchingSection.content}",
                sourceDocument = doc.fileName,
                sourceSection = matchingSection.title,
                isVerifiedFromEvidence = true
            )
        }

        return DocumentQAResult(
            answer = "I searched \"${doc.fileName}\", but found no section directly mentioning \"$query\".",
            sourceDocument = doc.fileName,
            sourceSection = "Not found",
            isVerifiedFromEvidence = false
        )
    }

    /**
     * Generates revision notes and extracts key concepts from an ingested document.
     */
    suspend fun generateRevisionNotes(docId: String): DocumentRevisionNotes? {
        val doc = documentDao.getDocumentByDocId(docId) ?: return null

        if (doc.isScanned) {
            return DocumentRevisionNotes(
                documentTitle = doc.fileName,
                pageCount = doc.pageCount,
                summary = "Scanned PDF limitation: Image-based document with no extractable text.",
                keyConcepts = listOf("OCR Required", "Scanned Document"),
                revisionPoints = listOf("Document requires optical character recognition before notes can be compiled.")
            )
        }

        val sections = documentDao.getSectionsForDocument(docId)
        val keyConcepts = mutableListOf<String>()
        val revisionPoints = mutableListOf<String>()

        sections.take(5).forEach { sec ->
            val firstSentence = sec.content.split(Regex("[.!?]\n|[.!?]\\s+")).firstOrNull()?.trim()
            if (!firstSentence.isNullOrBlank()) {
                revisionPoints.add("• ${sec.title}: $firstSentence.")
            }
        }

        doc.topics.split(",").forEach {
            if (it.isNotBlank()) keyConcepts.add(it.trim())
        }

        return DocumentRevisionNotes(
            documentTitle = doc.fileName,
            pageCount = doc.pageCount,
            summary = doc.summary.ifBlank { "Compiled from ${sections.size} verified sections." },
            keyConcepts = keyConcepts.ifEmpty { listOf("General Concepts", "Technical Overview") },
            revisionPoints = revisionPoints.ifEmpty { listOf("Review complete document sections for detailed study.") }
        )
    }
}
