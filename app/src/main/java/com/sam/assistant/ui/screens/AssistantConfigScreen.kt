package com.sam.assistant.ui.screens

import android.content.Context
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
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.interaction.SamAssistantService

@Composable
fun AssistantConfigScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isAssistantRoleHeld by remember { mutableStateOf(PermissionManager.isAssistantRoleHeld(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Assistant Integration",
            style = SamTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Connect SAM with Android's system Assistant architecture for seamless voice interaction.",
            style = SamTypography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Default Assistant Role
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default Digital Assistant",
                        style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set SAM as your primary Android voice assistant. Enables system assist gestures.",
                        style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (isAssistantRoleHeld) {
                    StatusBadge(text = "Active", color = SamSuccess)
                } else {
                    Button(
                        onClick = {
                            val intent = PermissionManager.getManageAssistantRoleIntent(context)
                            if (intent != null) context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Set Default", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Accessibility Service (Optional)
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Navigation Helper",
                        style = SamTypography.titleLarge.copy(fontSize = 16.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Optional AccessibilityService for hands-free system gestures (Back, Home, Recents).",
                        style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (isAccessibilityEnabled) {
                    StatusBadge(text = "Enabled", color = SamSuccess)
                } else {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(PermissionManager.getAccessibilitySettingsIntent())
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Enable", color = SamText, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Activation Standby Info
        GlassCard {
            Column {
                Text(
                    text = "Lock-State Security Policy",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, color = SamAccent)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• In standby mode, SAM detects \"Hey SAM\" only when the smartphone is unlocked.\n• When locked, microphone hardware access is instantly terminated.\n• Zero sensitive operations or notifications occur while screen is locked.",
                    style = SamTypography.bodyMedium.copy(fontSize = 13.sp, color = SamSecondaryText)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                SamAssistantService.start(context)
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Launch SAM Assistant", style = SamTypography.titleLarge.copy(fontSize = 15.sp))
        }
    }
}

