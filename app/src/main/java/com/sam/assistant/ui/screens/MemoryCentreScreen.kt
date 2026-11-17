package com.sam.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.sam.assistant.knowledge.memory.MemoryCategory
import com.sam.assistant.knowledge.memory.PersonalFactEntity
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryCentreScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val memoryEngine = app.knowledgeEngine.memoryEngine
    val scope = rememberCoroutineScope()

    val allFacts by memoryEngine.getAllMemoriesFlow().collectAsState(initial = emptyList())
    val isMemoryEnabled by memoryEngine.privacyController.isLongTermMemoryEnabled.collectAsState()
    val isPrivateMode by memoryEngine.privacyController.isPrivateModeActive.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String>("All") }
    var factToDelete by remember { mutableStateOf<PersonalFactEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val filteredFacts = remember(allFacts, searchQuery, selectedCategory) {
        allFacts.filter { fact ->
            val matchesCategory = selectedCategory == "All" || fact.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    fact.subject.contains(searchQuery, ignoreCase = true) ||
                    fact.content.contains(searchQuery, ignoreCase = true) ||
                    fact.tags.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Delete single fact confirmation
    factToDelete?.let { fact ->
        ConfirmationDialog(
            prompt = "Are you sure you want to delete this memory: \"${fact.subject}\"?",
            onConfirm = {
                scope.launch {
                    memoryEngine.deleteMemory(fact.factId)
                    factToDelete = null
                }
            },
            onDismiss = { factToDelete = null }
        )
    }

    // Delete all confirmation
    if (showDeleteAllDialog) {
        ConfirmationDialog(
            prompt = "Are you sure you want to delete ALL personal memories? This action is irreversible.",
            onConfirm = {
                scope.launch {
                    memoryEngine.deleteAllMemories()
                    showDeleteAllDialog = false
                    Toast.makeText(context, "All personal memories cleared.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showDeleteAllDialog = false }
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
                text = "Memory Centre",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SamText
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                scope.launch {
                    val export = memoryEngine.exportAsText()
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("SAM Memory Export", export)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Memory exported to clipboard.", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = "Export Memory", tint = SamPrimary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Privacy Controls Card
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Memory Privacy Controls",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SamText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Long-Term Memory", fontSize = 14.sp, color = SamText)
                                Text("Persist user-approved facts offline", fontSize = 12.sp, color = SamSecondaryText)
                            }
                            Switch(
                                checked = isMemoryEnabled,
                                onCheckedChange = { memoryEngine.privacyController.setLongTermMemoryEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SamPrimary, checkedTrackColor = SamPrimary.copy(alpha = 0.5f))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Private Mode", fontSize = 14.sp, color = SamText)
                                Text("Do not record new facts during sessions", fontSize = 12.sp, color = SamSecondaryText)
                            }
                            Switch(
                                checked = isPrivateMode,
                                onCheckedChange = { memoryEngine.privacyController.setPrivateMode(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SamWarning, checkedTrackColor = SamWarning.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            // Search Bar & Clear All
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search memories...", color = SamSecondaryText) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SamSecondaryText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SamText,
                            unfocusedTextColor = SamText,
                            focusedBorderColor = SamPrimary,
                            unfocusedBorderColor = SamCardBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (allFacts.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.background(SamError.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = SamError)
                        }
                    }
                }
            }

            // Categories Filter Chips
            item {
                val categories = listOf("All") + MemoryCategory.values().map { it.name }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.replace("_", " "), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamPrimary,
                                selectedLabelColor = SamText,
                                containerColor = SamDarkSurface,
                                labelColor = SamSecondaryText
                            )
                        )
                    }
                }
            }

            // Facts List
            if (filteredFacts.isEmpty()) {
                item {
                    GlassCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No memories match \"$searchQuery\"." else "No verified personal memories stored yet.\nSay \"Remember that I prefer studying in the evening\" or \"Remember my project ARES uses Postgres\".",
                                color = SamSecondaryText,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredFacts, key = { it.factId }) { fact ->
                    val badgeColor = when (fact.category) {
                        MemoryCategory.STUDY_PREFERENCES.name -> Color(0xFF00E676)
                        MemoryCategory.PROJECT_INFORMATION.name -> SamPrimary
                        MemoryCategory.APPLICATION_PREFERENCES.name -> Color(0xFF06B6D4)
                        MemoryCategory.ROUTINES.name -> SamWarning
                        else -> SamAccent
                    }

                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fact.subject,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SamText,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge(text = fact.category.replace("_", " "), color = badgeColor)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = fact.content,
                                fontSize = 14.sp,
                                color = SamText
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Source: ${fact.sourceReference}",
                                    fontSize = 11.sp,
                                    color = SamSecondaryText
                                )
                                IconButton(
                                    onClick = { factToDelete = fact },
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
