package com.sam.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.state.VoiceState

@Composable
fun VoiceOrb(
    state: VoiceState,
    modifier: Modifier = Modifier,
    sizeDp: Int = 180,
    audioRmsDb: Float = 0f,
    onClick: () -> Unit = {}
) {
    // Pulse animation for active states
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (state == VoiceState.LISTENING || state == VoiceState.WAKE_WORD_DETECTED) 1.15f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == VoiceState.LISTENING) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveAlpha"
    )

    // State-dependent color mapping
    val (primaryColor, accentColor) = when (state) {
        VoiceState.INACTIVE -> Pair(Color(0xFF64748B), Color(0xFF475569))
        VoiceState.STANDBY -> Pair(SamPrimary, SamAccent)
        VoiceState.WAKE_WORD_DETECTED -> Pair(SamAccent, Color(0xFFC084FC))
        VoiceState.LISTENING -> Pair(Color(0xFF06B6D4), Color(0xFF3B82F6))
        VoiceState.PROCESSING -> Pair(Color(0xFFF59E0B), Color(0xFFD97706))
        VoiceState.NEEDS_CLARIFICATION -> Pair(Color(0xFFA855F7), Color(0xFF9333EA))
        VoiceState.WAITING_FOR_CONFIRMATION -> Pair(Color(0xFFEC4899), Color(0xFFBE185D))
        VoiceState.SPEAKING -> Pair(SamSuccess, Color(0xFF10B981))
        VoiceState.FOLLOW_UP_LISTENING -> Pair(Color(0xFF38BDF8), Color(0xFF0284C7))
        VoiceState.COMPLETED -> Pair(SamSuccess, Color(0xFF059669))
        VoiceState.ERROR -> Pair(SamError, Color(0xFFDC2626))
    }

    val animatedPrimary by animateColorAsState(targetValue = primaryColor, label = "PrimaryColor")
    val animatedAccent by animateColorAsState(targetValue = accentColor, label = "AccentColor")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size((sizeDp * 1.3).dp)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Ripple wave canvas
            if (state == VoiceState.LISTENING || state == VoiceState.SPEAKING) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dynamicRadius = (size.minDimension / 2) * pulseScale + (audioRmsDb.coerceIn(0f, 40f) * 0.8f)
                    drawCircle(
                        color = animatedPrimary.copy(alpha = waveAlpha),
                        radius = dynamicRadius.coerceAtMost(size.minDimension / 2),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            // Core Orb
            Canvas(modifier = Modifier.size((sizeDp * pulseScale).dp)) {
                // Outer glow ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedPrimary.copy(alpha = 0.45f),
                            animatedAccent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2
                )
                // Center gradient sphere
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(animatedPrimary, animatedAccent)
                    ),
                    radius = (size.minDimension / 2) * 0.72f
                )
                // Highlighting inner circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    radius = (size.minDimension / 2) * 0.65f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Center Icon
            Icon(
                imageVector = if (state == VoiceState.INACTIVE) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = state.description,
                tint = SamText,
                modifier = Modifier.size((sizeDp * 0.32).dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // State indicator label
        Text(
            text = state.description,
            style = SamTypography.titleLarge.copy(
                color = animatedPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
        )
    }
}
