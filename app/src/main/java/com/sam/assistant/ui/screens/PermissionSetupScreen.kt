package com.sam.assistant.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*

@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasAudio by remember { mutableStateOf(PermissionManager.hasRecordAudioPermission(context)) }
    var hasContacts by remember { mutableStateOf(PermissionManager.hasContactsPermission(context)) }
    var hasCalls by remember { mutableStateOf(PermissionManager.hasCallPhonePermission(context)) }
    var hasSms by remember { mutableStateOf(PermissionManager.hasSmsPermission(context)) }
    var hasCamera by remember { mutableStateOf(PermissionManager.hasCameraPermission(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionManager.hasNotificationPermission(context)) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudio = granted
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        hasContacts = PermissionManager.hasContactsPermission(context)
        hasCalls = PermissionManager.hasCallPhonePermission(context)
        hasSms = PermissionManager.hasSmsPermission(context)
        hasCamera = PermissionManager.hasCameraPermission(context)
        hasNotifications = PermissionManager.hasNotificationPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Assistant Permissions",
            style = SamTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "SAM operates 100% locally. Permissions are strictly used for on-device voice processing and automation.",
            style = SamTypography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        Text(
            text = "CORE REQUIRED",
            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            title = "Microphone",
            description = "Essential for local wake-word (\"Hey SAM\") and speech recognition.",
            isGranted = hasAudio,
            icon = Icons.Default.Mic,
            onRequest = { audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "OPTIONAL SMARTPHONE AUTOMATION",
            style = SamTypography.labelSmall.copy(color = SamAccent, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            title = "Contacts & Calling",
            description = "Allows SAM to look up contacts and initiate calls with your voice confirmation.",
            isGranted = hasContacts && hasCalls,
            icon = Icons.Default.Phone,
            onRequest = {
                multipleLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.CALL_PHONE
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            title = "SMS Messaging",
            description = "Allows SAM to compose and send SMS messages with voice confirmation.",
            isGranted = hasSms,
            icon = Icons.Default.Chat,
            onRequest = {
                multipleLauncher.launch(arrayOf(android.Manifest.permission.SEND_SMS))
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            title = "Camera (Flashlight)",
            description = "Allows SAM to turn the torch on and off upon voice command.",
            isGranted = hasCamera,
            icon = Icons.Default.FlashlightOn,
            onRequest = {
                multipleLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            title = "Notifications & Alarms",
            description = "Required on Android 13+ to post reminder alarms and standby status.",
            isGranted = hasNotifications,
            icon = Icons.Default.Notifications,
            onRequest = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    multipleLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                }
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Proceed to Configuration", style = SamTypography.titleLarge.copy(fontSize = 15.sp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onRequest: () -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGranted) SamSuccess.copy(alpha = 0.15f) else SamPrimary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) SamSuccess else SamPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(text = title, style = SamTypography.titleLarge.copy(fontSize = 15.sp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = description, style = SamTypography.bodyMedium.copy(fontSize = 12.sp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                StatusBadge(text = "Granted", color = SamSuccess)
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Grant", color = SamText, fontSize = 12.sp)
                }
            }
        }
    }
}

