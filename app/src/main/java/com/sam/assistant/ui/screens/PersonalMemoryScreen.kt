package com.sam.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.memory.model.MemoryEntity
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PersonalMemoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val scope = rememberCoroutineScope()

    val memories by app.memoryRepository.getAllMemories().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredMemories = remember(memories, searchQuery, selectedCategory) {
        memories.filter { mem ->
            val matchesSearch = searchQuery.isBlank() ||
                    mem.key.contains(searchQuery, ignoreCase = true) ||
                    mem.value.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || mem.category.equals(selectedCategory, ignoreCase = true)
            matchesSearch && matchesCategory
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { key, value, category ->
                showAddDialog = false
                scope.launch {
                    app.memoryRepository.saveMemory(key = key, value = value, category = category)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = SamText)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Personal Memory",
                    style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            }

            IconButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Memory", tint = SamPrimary)
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories...", color = SamMutedText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SamSecondaryText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SamPrimary,
                unfocusedBorderColor = SamCardBorder,
                focusedTextColor = SamText,
                unfocusedTextColor = SamText
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Pills
        val categories = listOf("All", "Projects", "Preferences", "Personal", "Notes")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (cat in categories) {
                val isSelected = selectedCategory == cat
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) SamPrimary else SamDarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SamPrimary else SamCardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    TextButton(
                        onClick = { selectedCategory = cat },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = cat,
                            style = SamTypography.labelSmall.copy(
                                color = if (isSelected) SamText else SamSecondaryText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No personal memories found.\nSay \"Hey SAM, remember that...\" to save information.",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredMemories, key = { it.id }) { mem ->
                    MemoryCardItem(
                        memory = mem,
                        onDelete = {
                            scope.launch { app.memoryRepository.deleteMemory(mem.id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCardItem(
    memory: MemoryEntity,
    onDelete: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(text = memory.category, color = SamAccent)
                    if (memory.isEncrypted) {
                        StatusBadge(text = "Keystore Encrypted", color = SamPrimary, icon = Icons.Default.Lock)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = memory.key,
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memory.value,
                    style = SamTypography.bodyMedium.copy(color = SamText, fontSize = 13.sp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = SamError,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, category: String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Projects") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Personal Memory", style = SamTypography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Topic / Title", color = SamSecondaryText) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Content / Note", color = SamSecondaryText) }
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category", color = SamSecondaryText) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (key.isNotBlank()) onSave(key, value, category) },
                colors = ButtonDefaults.buttonColors(containerColor = SamPrimary)
            ) {
                Text("Save Encrypted")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = SamDarkSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

