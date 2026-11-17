package com.sam.assistant.knowledge.documents

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.UUID

sealed class IngestionResult {
    data class Success(val document: DocumentEntity, val sectionCount: Int, val message: String) : IngestionResult()
    data class ScannedLimitation(val document: DocumentEntity, val warning: String) : IngestionResult()
    data class Failed(val error: String) : IngestionResult()
}

class DocumentIngestionManager(
    private val dao: KnowledgeDocumentDao
) {
    companion object {
        private const val TAG = "DocumentIngestion"
        private const val SECTION_CHUNK_SIZE = 800 // characters per chunk
    }

    /**
     * Ingests a text or markdown document into the knowledge library.
     */
    suspend fun ingestText(
        fileName: String,
        content: String,
        uriString: String = "",
        mimeType: String = "text/plain"
    ): IngestionResult {
        if (content.isBlank()) {
            return IngestionResult.Failed("Document content is empty.")
        }

        val docId = "doc_" + UUID.randomUUID().toString().take(8)
        val sections = chunkContentIntoSections(docId, fileName, content)

        val summary = generateQuickSummary(content, fileName)
        val topics = extractTopics(content)

        val docEntity = DocumentEntity(
            docId = docId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = content.toByteArray().size.toLong(),
            uriString = uriString,
            isScanned = false,
            pageCount = 1,
            extractedTextLength = content.length,
            summary = summary,
            topics = topics,
            indexingStatus = "INDEXED"
        )

        dao.insertDocument(docEntity)
        dao.insertSections(sections)

        Log.d(TAG, "Ingested text document $fileName with ${sections.size} sections.")
        return IngestionResult.Success(docEntity, sections.size, "Document \"$fileName\" successfully ingested and indexed into knowledge base.")
    }

    /**
     * Ingests a PDF document. Extracts readable text and detects scanned image-only PDFs honestly.
     */
    suspend fun ingestPdf(
        fileName: String,
        extractedText: String,
        pageCount: Int,
        sizeBytes: Long,
        uriString: String = ""
    ): IngestionResult {
        val docId = "pdf_" + UUID.randomUUID().toString().take(8)

        // Scanned PDF detection: page count > 0 but extracted text is empty or whitespace
        if (extractedText.isBlank()) {
            val limitationEntity = DocumentEntity(
                docId = docId,
                fileName = fileName,
                mimeType = "application/pdf",
                sizeBytes = sizeBytes,
                uriString = uriString,
                isScanned = true,
                pageCount = pageCount,
                extractedTextLength = 0,
                summary = "Scanned document detected. No extractable text found.",
                topics = "Scanned",
                indexingStatus = "SCANNED_LIMITATION"
            )
            dao.insertDocument(limitationEntity)
            return IngestionResult.ScannedLimitation(
                limitationEntity,
                "Scanned PDF detected ($pageCount pages): Optical Character Recognition is required. Text could not be extracted without external OCR."
            )
        }

        val sections = chunkContentIntoSections(docId, fileName, extractedText)
        val summary = generateQuickSummary(extractedText, fileName)
        val topics = extractTopics(extractedText)

        val docEntity = DocumentEntity(
            docId = docId,
            fileName = fileName,
            mimeType = "application/pdf",
            sizeBytes = sizeBytes,
            uriString = uriString,
            isScanned = false,
            pageCount = pageCount,
            extractedTextLength = extractedText.length,
            summary = summary,
            topics = topics,
            indexingStatus = "INDEXED"
        )

        dao.insertDocument(docEntity)
        dao.insertSections(sections)

        Log.d(TAG, "Ingested PDF $fileName ($pageCount pages) into ${sections.size} sections.")
        return IngestionResult.Success(docEntity, sections.size, "PDF \"$fileName\" ($pageCount pages) successfully indexed.")
    }

    /**
     * Deletes a document and all of its associated sections atomically.
     */
    suspend fun deleteDocument(docId: String): Boolean {
        return dao.deleteDocumentAndSections(docId)
    }

    private fun chunkContentIntoSections(docId: String, docTitle: String, content: String): List<DocumentSectionEntity> {
        val sections = mutableListOf<DocumentSectionEntity>()
        val paragraphs = content.split(Regex("\n\n+|\r\n\r\n+")).filter { it.isNotBlank() }

        var currentChunk = StringBuilder()
        var sectionIndex = 1

        for (para in paragraphs) {
            if (currentChunk.length + para.length > SECTION_CHUNK_SIZE && currentChunk.isNotEmpty()) {
                val chunkText = currentChunk.toString().trim()
                sections.add(
                    DocumentSectionEntity(
                        docId = docId,
                        sectionIndex = sectionIndex++,
                        title = "$docTitle - Section ${sectionIndex - 1}",
                        content = chunkText,
                        tokens = tokenize(chunkText)
                    )
                )
                currentChunk = StringBuilder()
            }
            currentChunk.append(para).append("\n\n")
        }

        if (currentChunk.isNotBlank()) {
            val chunkText = currentChunk.toString().trim()
            sections.add(
                DocumentSectionEntity(
                    docId = docId,
                    sectionIndex = sectionIndex,
                    title = "$docTitle - Section $sectionIndex",
                    content = chunkText,
                    tokens = tokenize(chunkText)
                )
            )
        }

        return sections
    }

    private fun tokenize(text: String): String {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 }
            .distinct()
            .take(30)
            .joinToString(",")
    }

    private fun generateQuickSummary(content: String, title: String): String {
        val clean = content.replace(Regex("\\s+"), " ").trim()
        val snippet = if (clean.length > 200) clean.take(197) + "..." else clean
        return "Document \"$title\": $snippet"
    }

    private fun extractTopics(content: String): String {
        val topics = mutableListOf<String>()
        val lower = content.lowercase()
        if (lower.contains("operating system") || lower.contains("linux") || lower.contains("kernel") || lower.contains("process")) topics.add("Operating Systems")
        if (lower.contains("network") || lower.contains("tcp") || lower.contains("ip") || lower.contains("packet")) topics.add("Computer Networks")
        if (lower.contains("database") || lower.contains("sql") || lower.contains("query") || lower.contains("table")) topics.add("Databases")
        if (lower.contains("security") || lower.contains("cipher") || lower.contains("crypto") || lower.contains("vulnerability")) topics.add("Cybersecurity")
        if (lower.contains("algorithm") || lower.contains("sorting") || lower.contains("tree") || lower.contains("graph")) topics.add("Data Structures")
        if (lower.contains("ares") || lower.contains("architecture")) topics.add("System Architecture")
        return if (topics.isNotEmpty()) topics.joinToString(", ") else "General Knowledge"
    }
}
