package com.sam.assistant.ui.screens

import android.content.Context
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
import com.sam.assistant.memory.model.ConversationEntity
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val scope = rememberCoroutineScope()

    val conversations by app.memoryRepository.getAllConversations().collectAsState(initial = emptyList())
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmationDialog(
            prompt = "Clear all conversation history? This cannot be undone.",
            onConfirm = {
                showClearConfirm = false
                scope.launch { app.memoryRepository.clearConversations() }
            },
            onDismiss = { showClearConfirm = false }
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
                    text = "Conversation History",
                    style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            }

            if (conversations.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = SamError)
                }
            }
        }

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No previous conversations.",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(conversations.reversed(), key = { it.id }) { msg ->
                    ChatBubble(
                        entity = msg,
                        onDelete = {
                            scope.launch { app.memoryRepository.deleteConversation(msg.id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    entity: ConversationEntity,
    onDelete: () -> Unit
) {
    val isUser = entity.role.lowercase() == "user"
    val timeStr = remember(entity.timestamp) {
        SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(entity.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) SamPrimary else SamDarkSurface,
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, SamCardBorder),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "SAM",
                        style = SamTypography.labelSmall.copy(
                            color = if (isUser) SamText.copy(alpha = 0.8f) else SamAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = SamSecondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entity.content,
                    style = SamTypography.bodyMedium.copy(color = SamText, fontSize = 14.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeStr,
                    style = SamTypography.labelSmall.copy(
                        color = if (isUser) SamText.copy(alpha = 0.6f) else SamMutedText,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

