package com.sam.assistant.automation.documents

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sam.assistant.automation.executor.ExecutionResult
import java.io.BufferedReader
import java.io.InputStreamReader

data class DocumentInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

class DocumentAutomationManager(private val context: Context) {

    companion object {
        private const val TAG = "DocumentAutomationManager"
    }

    /**
     * Creates an intent for the Storage Access Framework (SAF) document picker.
     * Note: Does not ask for broad all-files storage permission.
     */
    fun createDocumentPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain", "text/markdown"))
        }
    }

    /**
     * Safely opens a document using the default viewer application on the device.
     */
    fun openDocument(uri: Uri, mimeType: String = "application/pdf"): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opening document.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open document viewer", e)
            ExecutionResult.Error("No viewer application found for document.")
        }
    }

    /**
     * Extracts text from a text/plain or markdown document URI.
     */
    fun extractTextFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from document URI", e)
            null
        }
    }

    /**
     * Inspects a PDF document via Android PdfRenderer.
     * Checks page count and confirms readable structure.
     */
    fun inspectPdf(uri: Uri): Pair<Int, Boolean>? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                Pair(pageCount, true)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF inspection failed", e)
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }
}
