package com.sam.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.knowledge.documents.DocumentEntity
import com.sam.assistant.knowledge.retrieval.RetrievalResultItem
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun KnowledgeLibraryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val docManager = app.knowledgeEngine.documentIngestionManager
    val docDao = app.knowledgeEngine.documentDao
    val retrievalEngine = app.knowledgeEngine.retrievalEngine
    val scope = rememberCoroutineScope()

    val documents by docDao.getAllDocuments().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RetrievalResultItem>>(emptyList()) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    docToDelete?.let { doc ->
        ConfirmationDialog(
            prompt = "Delete document \"${doc.fileName}\" and all of its indexed sections?",
            onConfirm = {
                scope.launch {
                    docManager.deleteDocument(doc.docId)
                    docToDelete = null
                    Toast.makeText(context, "Document deleted.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { docToDelete = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SamText)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Knowledge Library",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SamText
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.length > 2) {
                    scope.launch {
                        searchResults = retrievalEngine.retrieve(it)
                    }
                } else {
                    searchResults = emptyList()
                }
            },
            placeholder = { Text("Search indexed documents...", color = SamSecondaryText) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SamSecondaryText) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SamText,
                unfocusedTextColor = SamText,
                focusedBorderColor = SamPrimary,
                unfocusedBorderColor = SamCardBorder
            ),
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Search Results Section (if active)
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Search Results (${searchResults.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SamText
                    )
                }
                items(searchResults) { item ->
                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SamText)
                                StatusBadge(text = "Score: ${(item.score * 100).toInt()}%", color = SamPrimary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.content, fontSize = 13.sp, color = SamText)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(item.sourceCitation, fontSize = 11.sp, color = SamSecondaryText)
                        }
                    }
                }
            }

            // Documents List Header
            item {
                Text(
                    text = "Imported Documents (${documents.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SamText,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (documents.isEmpty()) {
                item {
                    GlassCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No documents imported yet.\nUse Android Storage Access Framework to import PDFs, Markdown, or text files.",
                                color = SamSecondaryText,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(documents, key = { it.docId }) { doc ->
                    val badgeColor = if (doc.isScanned) SamWarning else SamSuccess
                    val statusText = if (doc.isScanned) "Scanned Limitation" else "Indexed (${doc.pageCount} pgs)"

                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = doc.fileName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SamText,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge(text = statusText, color = badgeColor)
                            }

                            if (doc.isScanned) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = SamWarning.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = SamWarning, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Scanned document detected: Text could not be extracted without external OCR.",
                                            fontSize = 11.sp,
                                            color = SamWarning
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = doc.summary,
                                fontSize = 13.sp,
                                color = SamSecondaryText
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Topics: ${doc.topics}",
                                    fontSize = 11.sp,
                                    color = SamPrimary
                                )
                                IconButton(
                                    onClick = { docToDelete = doc },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SamSecondaryText, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
