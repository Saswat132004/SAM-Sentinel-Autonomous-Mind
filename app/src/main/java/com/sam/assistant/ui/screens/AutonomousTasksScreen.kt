package com.sam.assistant.ui.screens

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
import com.sam.assistant.agent.model.AgentState
import com.sam.assistant.agent.model.AgentTask
import com.sam.assistant.agent.model.TaskStatus
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AutonomousTasksScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val agent = app.autonomousAgent
    val coroutineScope = rememberCoroutineScope()

    val agentState by agent.state.collectAsState()
    val currentGoal by agent.currentGoal.collectAsState()
    val logs by agent.logs.collectAsState()
    val pendingApproval by agent.pendingApproval.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var taskList by remember { mutableStateOf<List<AgentTask>>(emptyList()) }

    // Update graph tasks when agent state updates
    LaunchedEffect(agentState) {
        taskList = agent.graphManager.graph.getAllTasks()
    }

    // High Risk Approval Dialog
    pendingApproval?.let { req ->
        ConfirmationDialog(
            prompt = req.prompt,
            onConfirm = { agent.approvePendingAction() },
            onDismiss = { agent.rejectPendingAction() }
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
                .padding(top = 40.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SamText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Autonomous Intelligence",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SamText
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Goal Input Card
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Autonomous Goal Prompt",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SamText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputPrompt,
                            onValueChange = { inputPrompt = it },
                            placeholder = { Text("e.g., Open Brave and search for cybersecurity news", color = SamSecondaryText) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = SamText,
                                unfocusedTextColor = SamText,
                                focusedBorderColor = SamPrimary,
                                unfocusedBorderColor = SamCardBorder
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (inputPrompt.isNotBlank()) {
                                        val prompt = inputPrompt
                                        inputPrompt = ""
                                        coroutineScope.launch {
                                            agent.processGoal(prompt)
                                            taskList = agent.graphManager.graph.getAllTasks()
                                        }
                                    }
                                },
                                enabled = agentState == AgentState.IDLE || agentState == AgentState.COMPLETED || agentState == AgentState.FAILED,
                                colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Execute Goal")
                            }

                            if (agentState == AgentState.EXECUTING_GRAPH || agentState == AgentState.AWAITING_APPROVAL) {
                                OutlinedButton(
                                    onClick = { agent.cancelExecution() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SamError),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            // Agent Status Card
            item {
                val stateColor = when (agentState) {
                    AgentState.COMPLETED -> SamSuccess
                    AgentState.FAILED -> SamError
                    AgentState.AWAITING_APPROVAL -> SamWarning
                    AgentState.EXECUTING_GRAPH, AgentState.GENERATING_PLAN -> SamPrimary
                    AgentState.CANCELLED -> SamMutedText
                    else -> SamSecondaryText
                }

                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Agent State",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SamText
                            )
                            StatusBadge(text = agentState.name, color = stateColor)
                        }

                        currentGoal?.let { goal ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Active Objective: ${goal.mainObjective}",
                                fontSize = 14.sp,
                                color = SamSecondaryText
                            )
                        }

                        if (agentState == AgentState.EXECUTING_GRAPH) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val progress = agent.graphManager.getProgressPercentage() / 100f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = SamPrimary,
                                trackColor = SamDarkBackground
                            )
                        }
                    }
                }
            }

            // Task Graph (DAG) Execution Nodes
            if (taskList.isNotEmpty()) {
                item {
                    Text(
                        text = "Execution Graph (${taskList.size} subtasks)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SamText,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(taskList) { task ->
                    val taskBadgeColor = when (task.status) {
                        TaskStatus.COMPLETED -> SamSuccess
                        TaskStatus.FAILED -> SamError
                        TaskStatus.EXECUTING -> SamPrimary
                        TaskStatus.WAITING_FOR_APPROVAL -> SamWarning
                        TaskStatus.BLOCKED -> SamError
                        TaskStatus.CANCELLED -> SamMutedText
                        else -> SamSecondaryText
                    }

                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.description,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SamText,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge(text = task.status.name, color = taskBadgeColor)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tool: ${task.toolName}",
                                    fontSize = 12.sp,
                                    color = SamPrimary
                                )
                                Text(
                                    text = "Risk: ${task.riskLevel.name}",
                                    fontSize = 12.sp,
                                    color = if (task.riskLevel.name == "HIGH") SamWarning else SamSecondaryText
                                )
                            }

                            if (task.dependencies.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Depends on: ${task.dependencies.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = SamSecondaryText
                                )
                            }

                            task.executionEvidence?.let { evidence ->
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = SamCardBorder.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Evidence: $evidence",
                                    fontSize = 12.sp,
                                    color = SamSuccess
                                )
                            }

                            task.errorMessage?.let { error ->
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = SamCardBorder.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Error: $error",
                                    fontSize = 12.sp,
                                    color = SamError
                                )
                            }
                        }
                    }
                }
            }

            // Live Execution Logs Terminal
            if (logs.isNotEmpty()) {
                item {
                    Text(
                        text = "Live Execution Logs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SamText,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .padding(12.dp)
                        ) {
                            LazyColumn(reverseLayout = true) {
                                items(logs.reversed()) { logEntry ->
                                    Text(
                                        text = logEntry,
                                        fontSize = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = if (logEntry.contains("failed") || logEntry.contains("denied")) SamError else SamSecondaryText,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
