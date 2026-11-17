package com.sam.assistant.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrivacyDashboardScreen(
    onBack: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToConversations: () -> Unit,
    onNavigateToCommands: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val memories by app.memoryRepository.getAllMemories().collectAsState(initial = emptyList())
    val conversations by app.memoryRepository.getAllConversations().collectAsState(initial = emptyList())
    val commands by app.memoryRepository.getAllCommandLogs().collectAsState(initial = emptyList())

    var saveTranscripts by remember { mutableStateOf(true) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }

    if (showDeleteAllConfirm) {
        ConfirmationDialog(
            prompt = "DELETE ALL DATA?\n\nThis will permanently wipe all personal memories, conversation history, and command logs. Android KeyStore credentials will be cleared.",
            onConfirm = {
                showDeleteAllConfirm = false
                scope.launch {
                    app.memoryRepository.deleteAllUserData()
                }
            },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = SamText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "SAM PRIVACY & MEMORY CENTER",
                    style = SamTypography.headlineMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SamPrimary)
                )
                Text(
                    text = "Transparent local data control & security dashboard",
                    style = SamTypography.labelSmall.copy(color = SamSecondaryText)
                )
            }
        }

        // Security Status Banner
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = SamSuccess.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = SamSuccess)
                    }
                }
                Column {
                    Text(
                        text = "100% Offline & Keystore Protected",
                        style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hardware-backed AES-256-GCM encryption. Zero trackers, no analytics, no cloud transmission.",
                        style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION A: Personal Memory
        PrivacySectionItem(
            icon = Icons.Default.Psychology,
            title = "A. Personal Memory",
            subtitle = "${memories.size} private memories stored locally with KeyStore encryption.",
            buttonText = "Inspect Memories",
            onAction = onNavigateToMemories
        )

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION B: Conversation History
        PrivacySectionItem(
            icon = Icons.Default.Forum,
            title = "B. Conversation History",
            subtitle = "${conversations.size} chat interactions stored on device.",
            buttonText = "Manage Chats",
            onAction = onNavigateToConversations
        )

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION C: Command History
        PrivacySectionItem(
            icon = Icons.Default.History,
            title = "C. Command History",
            subtitle = "${commands.size} automation actions logged locally.",
            buttonText = "View Log",
            onAction = onNavigateToCommands
        )

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION D: Voice Data & Transcripts
        GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "D. Voice Data Policy",
                            style = SamTypography.titleLarge.copy(fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Raw audio is never saved to disk. Microphone streams are processed live in RAM and discarded immediately.",
                            style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save text transcripts locally",
                        style = SamTypography.bodyMedium.copy(color = SamText, fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = saveTranscripts,
                        onCheckedChange = { saveTranscripts = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SamText,
                            checkedTrackColor = SamPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION E: Permissions & App Settings
        GlassCard {
            Column {
                Text(
                    text = "E. System Permissions",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify and manage Android system permission grants for SAM.",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { context.startActivity(PermissionManager.getAppSettingsIntent(context)) },
                    colors = ButtonDefaults.buttonColors(containerColor = SamCardBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android Permission Settings", color = SamText, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION F: Data Export
        GlassCard {
            Column {
                Text(
                    text = "F. Data Export",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Export your personal memories, conversations, and automation logs into a readable JSON file.",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val json = app.memoryRepository.exportAllDataToJson()
                            withContext(Dispatchers.Main) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    type = "application/json"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Export SAM Data (JSON)")
                                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(shareIntent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export All Data as JSON", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION G: Delete All Data
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SamError.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, SamError.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "G. Nuclear Data Wipe",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, color = SamError, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Irreversibly erase all stored personal memories, conversation history, command logs, and caches.",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp, color = SamSecondaryText)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showDeleteAllConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SamError),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Stored Data", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacySectionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = SamPrimary, modifier = Modifier.size(24.dp))
                Column {
                    Text(text = title, style = SamTypography.titleLarge.copy(fontSize = 15.sp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = subtitle, style = SamTypography.bodyMedium.copy(fontSize = 12.sp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(buttonText, color = SamText, fontSize = 12.sp)
            }
        }
    }
}

