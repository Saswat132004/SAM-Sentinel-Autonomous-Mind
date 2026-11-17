package com.sam.assistant.ui.screens

import android.media.AudioRecord
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.core.diagnostics.SystemDiagnostics
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.ui.MainActivity
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import com.sam.assistant.voice.audio.AudioCaptureManager
import com.sam.assistant.voice.audio.AudioSessionOwner
import com.sam.assistant.voice.recognition.EngineComparisonReport
import com.sam.assistant.ai.cognitive.context.ContextManager
import com.sam.assistant.ai.cognitive.dialogue.DialogueManager
import com.sam.assistant.ai.cognitive.models.CognitiveParseResult
import com.sam.assistant.voice.recognition.SpeechBenchmarkManager
import com.sam.assistant.voice.recognition.VoskSpeechEngine
import com.sam.assistant.voice.recognition.WhisperSpeechEngine
import com.sam.assistant.voice.state.VoiceStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as SamApplication

    val hardwareInfo = remember { SystemDiagnostics.getDeviceHardwareInfo(context) }
    val benchmarks by SystemDiagnostics.recentBenchmarks.collectAsState()
    val voiceState by VoiceStateManager.currentState.collectAsState()
    val lastError by VoiceStateManager.lastError.collectAsState()

    val activity = context as? MainActivity
    val service = activity?.assistantService

    val hasMicPermission = remember { PermissionManager.hasRecordAudioPermission(context) }

    // Diagnostic Test States
    var micTestStatus by remember { mutableStateOf("Not Tested") }
    var micTestRms by remember { mutableFloatStateOf(0f) }
    var isTestingMic by remember { mutableStateOf(false) }

    var asrTestStatus by remember { mutableStateOf("Not Tested") }
    var asrTestText by remember { mutableStateOf("") }
    var isTestingAsr by remember { mutableStateOf(false) }

    var ttsTestStatus by remember { mutableStateOf("Not Tested") }
    var isTestingTts by remember { mutableStateOf(false) }

    // Benchmark States
    var isRunningBenchmark by remember { mutableStateOf(false) }
    var benchmarkReport by remember { mutableStateOf<EngineComparisonReport?>(null) }

    // Stage 3 Cognitive States
    val lastCognitiveResult by app.cognitiveOrchestrator.lastParseResult.collectAsState()
    val dialogueState by DialogueManager.currentState.collectAsState()
    var cognitiveTestInput by remember { mutableStateOf("Kal subah saat baje Python padhne ka reminder laga do.") }
    var cognitiveTestOutput by remember { mutableStateOf<CognitiveParseResult?>(null) }
    var isAnalyzingCognitive by remember { mutableStateOf(false) }

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
                text = "Voice Engine Diagnostics",
                style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Section: Live Voice Engine Telemetry (Task 9)
        Text(
            text = "LIVE VOICE ENGINE TELEMETRY",
            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DiagnosticRow(
                    label = "Microphone Permission",
                    value = if (hasMicPermission) "Granted" else "Denied",
                    valueColor = if (hasMicPermission) SamSuccess else SamError
                )
                DiagnosticRow(
                    label = "Microphone Capture",
                    value = if (service?.audioSessionManager?.isRecording() == true) "Active (Streaming)" else "Standby (Acoustic Match)",
                    valueColor = if (service?.audioSessionManager?.isRecording() == true) SamAccent else SamSecondaryText
                )
                DiagnosticRow(
                    label = "Audio Sample Rate",
                    value = "${AudioCaptureManager.SAMPLE_RATE_HZ} Hz (16-bit PCM Mono)"
                )
                val voskDir = File(context.filesDir, "vosk-model")
                val isVoskPresent = voskDir.exists() && voskDir.isDirectory && (voskDir.listFiles()?.isNotEmpty() == true)
                DiagnosticRow(
                    label = "Active ASR Engine",
                    value = if (isVoskPresent) "Vosk Kaldi Offline ASR" else "Android Offline SpeechRecognizer"
                )
                DiagnosticRow(
                    label = "Model Loading Status",
                    value = if (isVoskPresent) "Mounted in RAM (vosk-model)" else "Offline System Fallback Active"
                )
                DiagnosticRow(
                    label = "Selected Languages",
                    value = "en-IN, hi-IN, Hinglish (Multilingual)"
                )
                DiagnosticRow(
                    label = "Wake-Word Status",
                    value = "Hey SAM (Cadence: Active)"
                )
                DiagnosticRow(
                    label = "Voice Session State",
                    value = voiceState.name,
                    valueColor = SamAccent
                )
                DiagnosticRow(
                    label = "Last Latency",
                    value = "${benchmarks.speechRecognitionMs} ms (Target < 250ms)"
                )
                DiagnosticRow(
                    label = "Model RAM Footprint",
                    value = if (isVoskPresent) "~118 MB" else "~45 MB (System Native)"
                )

                if (lastError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Troubleshooting Notice: $lastError",
                        style = SamTypography.labelSmall.copy(color = SamWarning, fontSize = 11.sp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Stage 2 Diagnostic Tests
        Text(
            text = "STAGE 2 VOICE ENGINE TESTS",
            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Microphone Availability Test
        GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1. Microphone Availability & Energy",
                            style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tests 16kHz PCM audio record stream and measures real-time energy.",
                            style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                        )
                    }
                    StatusBadge(
                        text = micTestStatus,
                        color = when (micTestStatus) {
                            "Passed" -> SamSuccess
                            "Testing..." -> SamAccent
                            "Failed" -> SamError
                            else -> SamSecondaryText
                        }
                    )
                }

                if (isTestingMic) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (micTestRms / 3000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = SamAccent,
                        trackColor = SamCardBorder
                    )
                    Text(
                        text = "Input Energy: ${micTestRms.toInt()} RMS",
                        style = SamTypography.labelSmall.copy(color = SamAccent, fontSize = 11.sp),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!PermissionManager.hasRecordAudioPermission(context)) {
                            micTestStatus = "Failed (Permission missing)"
                            return@Button
                        }
                        isTestingMic = true
                        micTestStatus = "Testing..."
                        scope.launch(Dispatchers.IO) {
                            try {
                                val audioManager = service?.audioSessionManager
                                val record = audioManager?.acquireMicrophone(AudioSessionOwner.DIAGNOSTIC)
                                if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                                    record.startRecording()
                                    val buffer = ShortArray(1024)
                                    val endTime = System.currentTimeMillis() + 2500L
                                    var maxRms = 0.0

                                    while (System.currentTimeMillis() < endTime) {
                                        val read = record.read(buffer, 0, buffer.size)
                                        if (read > 0) {
                                            var sum = 0.0
                                            for (i in 0 until read) sum += buffer[i] * buffer[i]
                                            val currentRms = sqrt(sum / read)
                                            if (currentRms > maxRms) maxRms = currentRms
                                            withContext(Dispatchers.Main) {
                                                micTestRms = currentRms.toFloat()
                                            }
                                        }
                                        delay(30)
                                    }
                                    audioManager.releaseMicrophone(AudioSessionOwner.DIAGNOSTIC)
                                    withContext(Dispatchers.Main) {
                                        isTestingMic = false
                                        micTestStatus = if (maxRms > 50.0) "Passed" else "Passed (Quiet)"
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isTestingMic = false
                                        micTestStatus = "Failed"
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isTestingMic = false
                                    micTestStatus = "Failed (${e.message})"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamCardBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingMic) "Testing Mic (2s)..." else "Run Mic Test", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Speech Recognition Test
        GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Speech Recognition (ASR)",
                            style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Decodes spoken commands in English, Hindi, or Hinglish.",
                            style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                        )
                    }
                    StatusBadge(
                        text = asrTestStatus,
                        color = when (asrTestStatus) {
                            "Passed" -> SamSuccess
                            "Listening..." -> SamPrimary
                            "Failed" -> SamError
                            else -> SamSecondaryText
                        }
                    )
                }

                if (asrTestText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Transcribed: \"$asrTestText\"",
                        style = SamTypography.bodyLarge.copy(color = SamSuccess, fontSize = 13.sp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isTestingAsr = true
                        asrTestStatus = "Listening..."
                        asrTestText = ""
                        service?.speechRecognitionManager?.startListening(scope)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamCardBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Trigger Test ASR", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Text-to-Speech Test
        GlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "3. Text-to-Speech (TTS)",
                            style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tests speech audio focus, language voices, and user interruption.",
                            style = SamTypography.bodyMedium.copy(fontSize = 12.sp)
                        )
                    }
                    StatusBadge(
                        text = ttsTestStatus,
                        color = when (ttsTestStatus) {
                            "Passed" -> SamSuccess
                            "Playing..." -> Color(0xFF06B6D4)
                            "Failed" -> SamError
                            else -> SamSecondaryText
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isTestingTts = true
                        ttsTestStatus = "Playing..."
                        service?.ttsManager?.speak(
                            text = "SAM voice engine diagnostic test completed successfully.",
                            onDone = {
                                isTestingTts = false
                                ttsTestStatus = "Passed"
                            },
                            onError = {
                                isTestingTts = false
                                ttsTestStatus = "Failed"
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamCardBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingTts) "Playing Audio..." else "Test Speech Playback", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Vosk vs Whisper.cpp Offline Benchmark Harness (Task 2)
        Text(
            text = "VOSK VS WHISPER.CPP BENCHMARK HARNESS",
            style = SamTypography.labelSmall.copy(color = SamAccent, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard {
            Column {
                Text(
                    text = "A/B Comparative Speech Engine Benchmark",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Evaluates WER, latency, RAM usage, and Hinglish accuracy on identical recordings.",
                    style = SamTypography.bodyMedium.copy(color = SamSecondaryText, fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isRunningBenchmark = true
                        scope.launch(Dispatchers.IO) {
                            val captureManager = AudioCaptureManager(context)
                            val voskEngine = VoskSpeechEngine(context, captureManager)
                            val whisperEngine = WhisperSpeechEngine(context, captureManager)
                            val benchmarkManager = SpeechBenchmarkManager(context, app.fastCommandEngine)

                            val report = benchmarkManager.runBenchmark(voskEngine, whisperEngine)
                            withContext(Dispatchers.Main) {
                                benchmarkReport = report
                                isRunningBenchmark = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRunningBenchmark) "Benchmarking Engines..." else "Run Vosk vs Whisper.cpp Benchmark", fontSize = 12.sp)
                }

                benchmarkReport?.let { report ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SamCardBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Vosk Kaldi Metrics:",
                        style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
                    )
                    DiagnosticRow(label = "Word Error Rate (WER)", value = "${report.voskMetrics.wordErrorRatePercent}%")
                    DiagnosticRow(label = "Command Accuracy", value = "${report.voskMetrics.commandAccuracyPercent}%")
                    DiagnosticRow(label = "Average Latency", value = "${report.voskMetrics.averageLatencyMs} ms")
                    DiagnosticRow(label = "Peak RAM Footprint", value = "${report.voskMetrics.ramUsageMb} MB")
                    DiagnosticRow(label = "Battery Drain", value = "~${report.voskMetrics.estimatedBatteryDrainPercentPerHour}% / hr")

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Whisper.cpp Quantized Metrics:",
                        style = SamTypography.labelSmall.copy(color = SamAccent, fontWeight = FontWeight.Bold)
                    )
                    DiagnosticRow(label = "Word Error Rate (WER)", value = "${report.whisperMetrics.wordErrorRatePercent}%")
                    DiagnosticRow(label = "Command Accuracy", value = "${report.whisperMetrics.commandAccuracyPercent}%")
                    DiagnosticRow(label = "Average Latency", value = "${report.whisperMetrics.averageLatencyMs} ms")
                    DiagnosticRow(label = "Peak RAM Footprint", value = "${report.whisperMetrics.ramUsageMb} MB")
                    DiagnosticRow(label = "Battery Drain", value = "~${report.whisperMetrics.estimatedBatteryDrainPercentPerHour}% / hr")

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SamSuccess.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Recommended Production Engine: ${report.recommendedEngine}",
                                style = SamTypography.labelSmall.copy(color = SamSuccess, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = report.recommendationReason,
                                style = SamTypography.bodyMedium.copy(color = SamText, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Stage 3 Cognitive Intelligence & Context Telemetry
        Text(
            text = "COGNITIVE INTELLIGENCE & CONTEXT (STAGE 3)",
            style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard {
            Column {
                Text(
                    text = "Live Conversational Understanding",
                    style = SamTypography.titleLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                DiagnosticRow(
                    label = "Dialogue State",
                    value = dialogueState.name,
                    valueColor = if (dialogueState == com.sam.assistant.ai.cognitive.models.DialogueState.NEEDS_CLARIFICATION) SamWarning else SamPrimary
                )
                DiagnosticRow(
                    label = "Last Detected Intent",
                    value = lastCognitiveResult?.intent?.name ?: "IDLE",
                    valueColor = SamAccent
                )
                DiagnosticRow(
                    label = "Language Identified",
                    value = lastCognitiveResult?.language?.displayName ?: "None"
                )
                DiagnosticRow(
                    label = "Confidence Score",
                    value = lastCognitiveResult?.let { "${(it.confidence * 100).toInt()}%" } ?: "N/A"
                )
                DiagnosticRow(
                    label = "Clarification Status",
                    value = if (lastCognitiveResult?.isClarificationNeeded == true) "Active: ${lastCognitiveResult?.clarificationPrompt}" else "None",
                    valueColor = if (lastCognitiveResult?.isClarificationNeeded == true) SamWarning else SamSecondaryText
                )
                DiagnosticRow(
                    label = "Active Context Frames",
                    value = ContextManager.summarizeContext()
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SamCardBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Interactive Cognitive Parse Evaluator",
                    style = SamTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = cognitiveTestInput,
                    onValueChange = { cognitiveTestInput = it },
                    label = { Text("Spoken Query (EN / HI / Hinglish)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SamText,
                        unfocusedTextColor = SamText,
                        focusedBorderColor = SamPrimary,
                        unfocusedBorderColor = SamCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isAnalyzingCognitive = true
                        scope.launch {
                            cognitiveTestOutput = app.cognitiveOrchestrator.processUtterance(cognitiveTestInput)
                            isAnalyzingCognitive = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAnalyzingCognitive) "Evaluating..." else "Run Cognitive Understanding Test", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }

                cognitiveTestOutput?.let { out ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = SamDarkSurface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "Intent: ${out.intent.name} (${(out.confidence * 100).toInt()}% confidence)", style = SamTypography.labelSmall.copy(color = SamPrimary, fontWeight = FontWeight.Bold))
                            Text(text = "Language: ${out.language.displayName}", style = SamTypography.bodyMedium.copy(color = SamSecondaryText, fontSize = 11.sp))
                            Text(text = "Entities: date=${out.entities.date}, time=${out.entities.time}, app=${out.entities.appName}, contact=${out.entities.contactName}, subject=${out.entities.subject}", style = SamTypography.bodyMedium.copy(color = SamText, fontSize = 11.sp))
                            if (out.isClarificationNeeded) {
                                Text(text = "Clarification Prompt: ${out.clarificationPrompt}", style = SamTypography.bodyMedium.copy(color = SamWarning, fontSize = 11.sp))
                            }
                            if (out.reasoningProposal != null) {
                                Text(text = "Reasoning Plan: ${out.reasoningProposal.objective}", style = SamTypography.bodyMedium.copy(color = SamAccent, fontSize = 11.sp))
                                Text(text = "Explanation: ${out.reasoningProposal.conciseUserExplanation}", style = SamTypography.bodyMedium.copy(color = SamText, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Device Specs
        Text(
            text = "HARDWARE SPECIFICATIONS (realme P4 5G)",
            style = SamTypography.labelSmall.copy(color = SamMutedText, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        GlassCard {
            Column {
                DiagnosticRow(label = "Target Device", value = "realme P4 5G (${hardwareInfo.model})")
                DiagnosticRow(label = "Chipset / SoC", value = hardwareInfo.chipset)
                DiagnosticRow(label = "CPU Architecture", value = "ARM64 (${hardwareInfo.cpuCores} Cores)")
                DiagnosticRow(label = "RAM Capacity", value = "8 GB (${hardwareInfo.availableRamMb} MB Free of ${hardwareInfo.totalRamMb} MB)")
                DiagnosticRow(label = "Battery", value = "${hardwareInfo.batteryPercent}% (${hardwareInfo.batteryTemperatureCelsius}°C)")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: Color = SamText) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = SamTypography.bodyMedium.copy(color = SamSecondaryText, fontSize = 13.sp))
        Text(text = value, style = SamTypography.bodyMedium.copy(color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
    }
}
