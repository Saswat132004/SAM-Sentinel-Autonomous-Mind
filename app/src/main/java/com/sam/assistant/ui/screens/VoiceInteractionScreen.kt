package com.sam.assistant.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.sam.assistant.ui.components.ConfirmationDialog
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.VoiceOrb
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.interaction.SamAssistantService
import com.sam.assistant.voice.state.VoiceState
import com.sam.assistant.voice.state.VoiceStateManager

@Composable
fun VoiceInteractionScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication

    val voiceState by VoiceStateManager.currentState.collectAsState()
    val lastRecognizedText by VoiceStateManager.lastRecognizedText.collectAsState()
    val lastResponseText by VoiceStateManager.lastSpokenText.collectAsState()
    val audioRmsDb by VoiceStateManager.audioRmsDb.collectAsState()

    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SamSecondaryText)
            }
            Text(
                text = "Voice Assistant",
                style = SamTypography.titleLarge.copy(fontSize = 16.sp)
            )
            IconButton(onClick = {
                (context as? com.sam.assistant.ui.MainActivity)?.assistantService?.triggerManualVoiceInput()
            }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart Voice", tint = SamPrimary)
            }
        }

        // Live Voice Orb & Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            VoiceOrb(
                state = voiceState,
                sizeDp = 190,
                audioRmsDb = audioRmsDb,
                onClick = {
                    (context as? com.sam.assistant.ui.MainActivity)?.assistantService?.triggerManualVoiceInput()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Live User Transcript & Cognitive Telemetry
            val cognitiveResult by app.cognitiveOrchestrator.lastParseResult.collectAsState()

            if (lastRecognizedText.isNotEmpty()) {
                GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You said:",
                            style = SamTypography.labelSmall.copy(color = SamSecondaryText)
                        )
                        cognitiveResult?.let { cog ->
                            Text(
                                text = "${cog.language.displayName} • ${cog.intent.name}",
                                style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$lastRecognizedText\"",
                        style = SamTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // SAM Spoken Response
            if (lastResponseText.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SamPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SAM:",
                            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastResponseText,
                            style = SamTypography.bodyLarge.copy(color = SamText, fontSize = 15.sp)
                        )
                    }
                }
            }
        }

        // Bottom manual text entry fallback
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type a command...", color = SamMutedText) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SamPrimary,
                    unfocusedBorderColor = SamCardBorder,
                    focusedTextColor = SamText,
                    unfocusedTextColor = SamText
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val input = textInput
                        textInput = ""
                        (context as? com.sam.assistant.ui.MainActivity)?.sendManualCommand(input)
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(SamPrimary, RoundedCornerShape(14.dp))
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = SamText)
            }
        }
    }
}
