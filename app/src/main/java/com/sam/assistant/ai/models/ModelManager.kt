package com.sam.assistant.ai.models

import android.content.Context
import android.os.Environment
import com.sam.assistant.core.diagnostics.SystemDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val presetModels = listOf(
        ModelConfig(
            id = "smollm2-360m-q4",
            name = "SmolLM2 360M Instruct",
            filename = "smollm2-360m-instruct-q4_k_m.gguf",
            parameterSize = "360M",
            quantization = "Q4_K_M",
            fileSizeBytes = 229_000_000L, // ~229 MB
            requiredRamMb = 450,
            description = "Ultra-lightweight reasoning model. Instant load time, minimal battery impact. Perfect for realme P4 5G standby.",
            isRecommended = true
        ),
        ModelConfig(
            id = "qwen2.5-0.5b-q4",
            name = "Qwen 2.5 0.5B Instruct",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            parameterSize = "0.5B",
            quantization = "Q4_K_M",
            fileSizeBytes = 398_000_000L, // ~398 MB
            requiredRamMb = 650,
            description = "Strong multilingual understanding & structured intent planning in under 400 MB.",
            isRecommended = false
        ),
        ModelConfig(
            id = "smollm2-1.7b-q4",
            name = "SmolLM2 1.7B Instruct",
            filename = "smollm2-1.7b-instruct-q4_k_m.gguf",
            parameterSize = "1.7B",
            quantization = "Q4_K_M",
            fileSizeBytes = 1_060_000_000L, // ~1.06 GB
            requiredRamMb = 1400,
            description = "High accuracy assistant model for deep conversation and multi-step workflow planning.",
            isRecommended = false
        ),
        ModelConfig(
            id = "gemma-2-2b-q4",
            name = "Gemma 2 2B Instruct",
            filename = "gemma-2-2b-it-q4_k_m.gguf",
            parameterSize = "2B",
            quantization = "Q4_k_m",
            fileSizeBytes = 1_600_000_000L, // ~1.6 GB
            requiredRamMb = 2100,
            description = "Google Gemma architecture. High reasoning quality for complex personal smartphone tasks.",
            isRecommended = false
        )
    )

    private val _availableModels = MutableStateFlow<List<ModelConfig>>(emptyList())
    val availableModels: StateFlow<List<ModelConfig>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelConfig?>(null)
    val selectedModel: StateFlow<ModelConfig?> = _selectedModel.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        val hardwareInfo = SystemDiagnostics.getDeviceHardwareInfo(context)
        val filesInDir = modelsDir.listFiles { file -> file.extension.lowercase() == "gguf" }?.toList() ?: emptyList()

        val updated = presetModels.map { preset ->
            val matchingFile = filesInDir.firstOrNull { it.name.equals(preset.filename, ignoreCase = true) }
            val isDownloaded = matchingFile != null && matchingFile.length() > 0
            preset.copy(
                isDownloaded = isDownloaded,
                localPath = matchingFile?.absolutePath
            )
        }.toMutableList()

        // Also detect custom imported models
        val presetFilenames = presetModels.map { it.filename.lowercase() }.toSet()
        for (customFile in filesInDir) {
            if (!presetFilenames.contains(customFile.name.lowercase())) {
                updated.add(
                    ModelConfig(
                        id = "custom-${customFile.name}",
                        name = "Custom: ${customFile.nameWithoutExtension}",
                        filename = customFile.name,
                        parameterSize = "Custom",
                        quantization = "GGUF",
                        fileSizeBytes = customFile.length(),
                        requiredRamMb = (customFile.length() / (1024 * 1024) * 1.3).toInt(),
                        description = "User-imported GGUF model.",
                        isRecommended = false,
                        isDownloaded = true,
                        localPath = customFile.absolutePath
                    )
                )
            }
        }

        _availableModels.value = updated

        // Select default model (prefer downloaded, otherwise recommended)
        val downloaded = updated.firstOrNull { it.isDownloaded }
        val defaultModel = downloaded ?: updated.firstOrNull { it.isRecommended } ?: updated.firstOrNull()
        if (_selectedModel.value == null || !_availableModels.value.any { it.id == _selectedModel.value?.id }) {
            _selectedModel.value = defaultModel
        }
    }

    fun selectModel(modelId: String) {
        val model = _availableModels.value.firstOrNull { it.id == modelId }
        if (model != null) {
            _selectedModel.value = model
        }
    }

    fun canRunModel(model: ModelConfig): Boolean {
        val hardware = SystemDiagnostics.getDeviceHardwareInfo(context)
        // Keep at least 500 MB RAM free for OS and UI
        return (hardware.availableRamMb - 500) > model.requiredRamMb
    }

    fun getModelsDirectory(): File = modelsDir
}

