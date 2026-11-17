package com.sam.assistant.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.R
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.theme.*

@Composable
fun WelcomeOnboardingScreen(
    onGetStarted: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamDarkBackground)
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // App Icon
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(24.dp),
                color = SamPrimary.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(2.dp, SamPrimary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "SAM Logo",
                        tint = SamPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Meet SAM",
                style = SamTypography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Secure Autonomous Mobile Assistant",
                style = SamTypography.bodyMedium.copy(color = SamAccent, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Voice-first. 100% offline. Zero cloud transmission.\nBuilt natively for your realme P4 5G.",
                style = SamTypography.bodyLarge.copy(color = SamSecondaryText, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Value pillars
            FeatureItem(
                icon = Icons.Default.Mic,
                title = "Voice Wake Word",
                description = "Activate hands-free with \"Hey SAM\" whenever your phone is unlocked."
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureItem(
                icon = Icons.Default.Lock,
                title = "Lock-State Security",
                description = "Microphone shuts down instantly when the phone is locked. No background leaks."
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureItem(
                icon = Icons.Default.Memory,
                title = "Local AI Brain",
                description = "Fast deterministic commands + quantized on-device LLMs without internet access."
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureItem(
                icon = Icons.Default.Shield,
                title = "Transparent Memory",
                description = "Store private information encrypted with hardware-backed Android KeyStore."
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Configure Assistant",
                style = SamTypography.titleLarge.copy(color = SamText, fontSize = 16.sp)
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    GlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = SamPrimary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SamPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = SamTypography.bodyMedium.copy(fontSize = 12.sp, color = SamSecondaryText)
                )
            }
        }
    }
}

