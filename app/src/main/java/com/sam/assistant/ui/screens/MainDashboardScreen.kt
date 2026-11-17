package com.sam.assistant.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.core.diagnostics.SystemDiagnostics
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.memory.model.CommandLogEntity
import com.sam.assistant.ui.components.*
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.interaction.SamAssistantService
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager

@Composable
fun MainDashboardScreen(
    onNavigateToVoice: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAutonomous: () -> Unit = {},
    onNavigateToStudy: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    onNavigateToCybersecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication

    val voiceState by VoiceStateManager.currentState.collectAsState()
    val lastRecognizedText by VoiceStateManager.lastRecognizedText.collectAsState()
    val lastError by VoiceStateManager.lastError.collectAsState()
    val audioRmsDb by VoiceStateManager.audioRmsDb.collectAsState()

    val isLocked by LockStateManager.isLocked.collectAsState()
    val hasAudioPermission = remember { PermissionManager.hasRecordAudioPermission(context) }
    val selectedModel by app.modelManager.selectedModel.collectAsState()
    val hardwareInfo = remember { SystemDiagnostics.getDeviceHardwareInfo(context) }

    val recentCommands by app.memoryRepository.getAllCommandLogs().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAM",
                        style = SamTypography.headlineLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "realme P4 5G • Dimensity 7400 Ultra",
                        style = SamTypography.labelSmall.copy(color = SamSecondaryText)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Diagnostics",
                            tint = SamSecondaryText
                        )
                    }
                    IconButton(onClick = onNavigateToPrivacy) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Privacy Center",
                            tint = SamPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stage 2 Voice Engine Status Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    text = if (isLocked) "Device Locked" else "Wake Word: Hey SAM",
                    color = if (isLocked) SamSecondaryText else SamSuccess,
                    icon = if (isLocked) Icons.Default.Lock else Icons.Default.Mic
                )
                StatusBadge(
                    text = if (hasAudioPermission) "Mic Granted" else "Mic Denied",
                    color = if (hasAudioPermission) SamPrimary else SamError,
                    icon = Icons.Default.Security
                )
                StatusBadge(
                    text = "Offline ASR Ready",
                    color = Color(0xFF06B6D4),
                    icon = Icons.Default.CloudOff
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Central Voice Orb with Live Audio Feedback
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VoiceOrb(
                    state = voiceState,
                    audioRmsDb = audioRmsDb,
                    onClick = {
                        SamAssistantService.start(context)
                        onNavigateToVoice()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live recognized command card (if available)
            if (lastRecognizedText.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SamPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Last Spoken Command:",
                            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"$lastRecognizedText\"",
                            style = SamTypography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Error display banner (if error occurred)
            if (lastError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SamError.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamError.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = SamError, modifier = Modifier.size(18.dp))
                        Text(
                            text = lastError ?: "",
                            style = SamTypography.bodyMedium.copy(color = SamError, fontSize = 12.sp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Hardware & Spec Metrics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Available RAM",
                    value = "${hardwareInfo.availableRamMb} MB",
                    subtitle = "of ${hardwareInfo.totalRamMb} MB",
                    icon = Icons.Default.Memory,
                    accentColor = SamPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Battery",
                    value = "${hardwareInfo.batteryPercent}%",
                    subtitle = if (hardwareInfo.isCharging) "Charging" else "${hardwareInfo.batteryTemperatureCelsius}°C",
                    icon = Icons.Default.BatteryChargingFull,
                    accentColor = SamSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Voice Engine",
                    value = "Stage 2 Active",
                    subtitle = "Vosk + Cadence",
                    icon = Icons.Default.GraphicEq,
                    accentColor = SamAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Autonomous Agent",
                    value = "Stage 5 Active",
                    subtitle = "DAG Tasks & Tools",
                    icon = Icons.Default.SmartToy,
                    accentColor = Color(0xFF00E676),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToAutonomous() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Study Assistant",
                    value = "Stage 6 Active",
                    subtitle = "Quizzes & Flashcards",
                    icon = Icons.Default.MenuBook,
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToStudy() }
                )
                MetricCard(
                    title = "Knowledge Base",
                    value = "Hybrid Memory",
                    subtitle = "Docs & Preferences",
                    icon = Icons.Default.FolderSpecial,
                    accentColor = Color(0xFFFBBF24),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToKnowledge() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Cybersecurity",
                    value = "Stage 7 Active",
                    subtitle = "Wi-Fi & Traffic Audit",
                    icon = Icons.Default.Security,
                    accentColor = Color(0xFFEF4444),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToCybersecurity() }
                )
                MetricCard(
                    title = "Local Intelligence",
                    value = "100% Offline",
                    subtitle = "Privacy-Guaranteed",
                    icon = Icons.Default.VerifiedUser,
                    accentColor = SamSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Recent Commands Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Commands",
                    style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                )
                TextButton(onClick = onNavigateToHistory) {
                    Text("View All", color = SamPrimary, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Recent Commands List
        if (recentCommands.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "No commands executed yet.\nTry saying \"Hey SAM, what is artificial intelligence?\" or \"Hey SAM, open YouTube\".",
                        style = SamTypography.bodyMedium.copy(color = SamSecondaryText)
                    )
                }
            }
        } else {
            items(recentCommands.take(4)) { cmd ->
                CommandItem(cmd)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CommandItem(log: CommandLogEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SamCardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SamCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.command,
                    style = SamTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                )
                if (log.details.isNotEmpty()) {
                    Text(
                        text = log.details,
                        style = SamTypography.labelSmall.copy(color = SamSecondaryText, fontSize = 12.sp)
                    )
                }
            }

            val statusColor = when (log.status) {
                "SUCCESS" -> SamSuccess
                "REQUIRES_CONFIRMATION" -> SamWarning
                "PERMISSION_DENIED" -> SamError
                else -> SamSecondaryText
            }
            StatusBadge(text = log.status, color = statusColor)
        }
    }
}
