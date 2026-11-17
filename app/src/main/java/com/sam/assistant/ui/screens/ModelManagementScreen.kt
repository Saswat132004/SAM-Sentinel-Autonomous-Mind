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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.ai.models.ModelConfig
import com.sam.assistant.core.diagnostics.SystemDiagnostics
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*

@Composable
fun ModelManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication

    val availableModels by app.modelManager.availableModels.collectAsState()
    val selectedModel by app.modelManager.selectedModel.collectAsState()
    val hardwareInfo = remember { SystemDiagnostics.getDeviceHardwareInfo(context) }

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
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = SamText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "AI Model Management",
                style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        }

        // RAM Budget Card
        GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device RAM Budget",
                        style = SamTypography.titleLarge.copy(fontSize = 15.sp)
                    )
                    Text(
                        text = "${hardwareInfo.availableRamMb} MB Available",
                        style = SamTypography.labelSmall.copy(color = SamSuccess, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (hardwareInfo.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = SamPrimary,
                    trackColor = SamCardBorder
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "RAM Usage: ${hardwareInfo.ramUsagePercent}% of ${hardwareInfo.totalRamMb} MB. Keep models below available RAM for smooth execution.",
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "EVALUATED COMPACT MODELS",
            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(availableModels, key = { it.id }) { model ->
                ModelItemCard(
                    model = model,
                    isSelected = selectedModel?.id == model.id,
                    canRun = app.modelManager.canRunModel(model),
                    onSelect = { app.modelManager.selectModel(model.id) }
                )
            }
        }
    }
}

@Composable
private fun ModelItemCard(
    model: ModelConfig,
    isSelected: Boolean,
    canRun: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SamPrimary.copy(alpha = 0.12f) else SamDarkSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) SamPrimary else SamCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.name,
                        style = SamTypography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    if (model.isRecommended) {
                        StatusBadge(text = "Recommended", color = SamSuccess)
                    }
                }

                if (isSelected) {
                    StatusBadge(text = "Active", color = SamPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = model.description,
                style = SamTypography.bodyMedium.copy(fontSize = 12.sp, color = SamSecondaryText)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specs badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = "Params: ${model.parameterSize}", color = SamAccent)
                StatusBadge(text = "Format: ${model.quantization}", color = Color(0xFF06B6D4))
                StatusBadge(
                    text = "RAM: ~${model.requiredRamMb} MB",
                    color = if (canRun) SamSuccess else SamWarning
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Size: ${model.fileSizeBytes / (1024 * 1024)} MB",
                    style = SamTypography.labelSmall.copy(color = SamMutedText)
                )

                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) SamPrimary else SamCardBorder
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (isSelected) "Active Model" else "Select Model", fontSize = 12.sp)
                }
            }
        }
    }
}

