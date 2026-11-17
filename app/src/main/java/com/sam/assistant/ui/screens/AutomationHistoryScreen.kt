package com.sam.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import com.sam.assistant.automation.core.ExecutionTracker
import com.sam.assistant.automation.core.TaskExecutionStatus
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication

    val commandLogs by app.memoryRepository.getAllCommandLogs().collectAsState(initial = emptyList())
    val executionSnapshot by ExecutionTracker.snapshot.collectAsState()

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
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SamText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Automation History",
                style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Live Execution Tracker Card (Stage 4)
        if (executionSnapshot.status != TaskExecutionStatus.IDLE) {
            GlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE TASK EXECUTION",
                            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
                        )
                        val badgeColor = when (executionSnapshot.status) {
                            TaskExecutionStatus.TASK_COMPLETED -> SamSuccess
                            TaskExecutionStatus.FAILED, TaskExecutionStatus.CANCELLED -> SamError
                            TaskExecutionStatus.EXECUTING_STEP, TaskExecutionStatus.VERIFYING -> SamWarning
                            else -> SamSecondaryText
                        }
                        StatusBadge(
                            text = executionSnapshot.status.name,
                            color = badgeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = executionSnapshot.taskDescription,
                        style = SamTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                    if (executionSnapshot.currentStepDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Step ${executionSnapshot.currentStepIndex}/${executionSnapshot.totalSteps}: ${executionSnapshot.currentStepDescription}",
                            style = SamTypography.bodyMedium.copy(color = SamSecondaryText, fontSize = 12.sp)
                        )
                    }
                    if (executionSnapshot.status == TaskExecutionStatus.EXECUTING_STEP || executionSnapshot.status == TaskExecutionStatus.AWAITING_CONFIRMATION) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { ExecutionTracker.cancelTask() },
                            colors = ButtonDefaults.buttonColors(containerColor = SamCardBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Task Execution", fontSize = 11.sp, color = SamError)
                        }
                    }
                }
            }
        }

        if (commandLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No automation actions recorded yet.",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(commandLogs, key = { it.id }) { log ->
                    CommandLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun CommandLogItem(log: CommandLogEntity) {
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("h:mm:ss a, MMM d", Locale.getDefault()).format(Date(log.timestamp))
    }

    val (statusColor, icon) = when (log.status) {
        "SUCCESS" -> Pair(SamSuccess, Icons.Default.CheckCircle)
        "REQUIRES_CONFIRMATION" -> Pair(SamWarning, Icons.AutoMirrored.Filled.HelpOutline)
        "PERMISSION_DENIED" -> Pair(SamError, Icons.Default.DoNotDisturb)
        "BLOCKED" -> Pair(SamError, Icons.Default.Block)
        else -> Pair(SamSecondaryText, Icons.Default.Info)
    }

    GlassCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.actionType,
                    style = SamTypography.labelSmall.copy(color = SamAccent, fontWeight = FontWeight.Bold)
                )
                StatusBadge(text = log.status, color = statusColor, icon = icon)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "\"${log.command}\"",
                style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )

            if (log.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText, fontSize = 12.sp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = timeStr,
                style = SamTypography.labelSmall.copy(color = SamMutedText, fontSize = 11.sp),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

