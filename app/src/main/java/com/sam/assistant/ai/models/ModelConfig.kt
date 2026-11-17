package com.sam.assistant.ai.models

data class ModelConfig(
    val id: String,
    val name: String,
    val filename: String,
    val parameterSize: String, // e.g. "360M", "0.5B", "1.5B", "2B"
    val quantization: String,  // e.g. "Q4_K_M", "Q4_0"
    val fileSizeBytes: Long,
    val requiredRamMb: Int,
    val description: String,
    val isRecommended: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)

