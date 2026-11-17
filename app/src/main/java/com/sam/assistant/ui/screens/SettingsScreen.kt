package com.sam.assistant.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.interaction.SamAssistantService

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var wakeWordEnabled by remember { mutableStateOf(true) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var speechPitch by remember { mutableFloatStateOf(1.05f) }
    var confirmAllActions by remember { mutableStateOf(false) }

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
            Text(
                text = "Assistant Settings",
                style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Wake Word Section
        GlassCard {
            Column {
                Text(
                    text = "Wake Word Configuration",
                    style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Activation phrase: \"Hey SAM\"",
                    style = SamTypography.bodyMedium.copy(color = SamAccent, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Standby wake-word listener",
                        style = SamTypography.bodyMedium.copy(color = SamText)
                    )
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = {
                            wakeWordEnabled = it
                            if (it) SamAssistantService.start(context)
                            else SamAssistantService.stop(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SamText, checkedTrackColor = SamPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Text-to-Speech Settings
        GlassCard {
            Column {
                Text(
                    text = "Voice Feedback & Text-to-Speech",
                    style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Speech Rate: ${String.format("%.1f", speechRate)}x",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText)
                )
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = SamPrimary, activeTrackColor = SamPrimary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Pitch: ${String.format("%.2f", speechPitch)}",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText)
                )
                Slider(
                    value = speechPitch,
                    onValueChange = { speechPitch = it },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = SamAccent, activeTrackColor = SamAccent)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Execution Security Policy
        GlassCard {
            Column {
                Text(
                    text = "Execution Security",
                    style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sensitive actions (calls, SMS, memory deletion) always require voice confirmation.",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Require confirmation for all actions",
                        style = SamTypography.bodyMedium.copy(color = SamText)
                    )
                    Switch(
                        checked = confirmAllActions,
                        onCheckedChange = { confirmAllActions = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SamText, checkedTrackColor = SamPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware details
        GlassCard {
            Column {
                Text(
                    text = "Device Optimization Profile",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, color = SamSuccess)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Target: realme P4 5G\nChipset: MediaTek Dimensity 7400 Ultra\nAudio Pipeline: 16 kHz PCM Mono\nInference: GGUF 4-bit Quantization",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp, color = SamSecondaryText)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

