package com.sam.assistant.knowledge.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

class MemoryPrivacyController {

    private val _isLongTermMemoryEnabled = MutableStateFlow(true)
    val isLongTermMemoryEnabled: StateFlow<Boolean> = _isLongTermMemoryEnabled.asStateFlow()

    private val _isPrivateModeActive = MutableStateFlow(false)
    val isPrivateModeActive: StateFlow<Boolean> = _isPrivateModeActive.asStateFlow()

    companion object {
        private val SENSITIVE_PATTERNS = listOf(
            Pattern.compile("(?i)\\b(?:password|passwd|pwd)\\s*(?:is|:|=)\\s*\\S+"),
            Pattern.compile("(?i)\\b(?:api[_-]?key|secret[_-]?token|bearer\\s+[a-zA-Z0-9_.-]+)"),
            Pattern.compile("(?i)\\b(?:cvv|cvc)\\s*(?:is|:|=)?\\s*\\d{3,4}\\b"),
            Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b"), // Credit card regex
            Pattern.compile("(?i)\\b(?:private[_-]?key|begin\\s+rsa\\s+private\\s+key)")
        )
    }

    fun setLongTermMemoryEnabled(enabled: Boolean) {
        _isLongTermMemoryEnabled.value = enabled
    }

    fun setPrivateMode(active: Boolean) {
        _isPrivateModeActive.value = active
    }

    /**
     * Checks if a fact is allowed to be saved into persistent long-term storage.
     */
    fun canPersistMemory(content: String, isExplicitlyRequested: Boolean, isUserApproved: Boolean): PrivacyCheckResult {
        if (!_isLongTermMemoryEnabled.value) {
            return PrivacyCheckResult.Denied("Long-term memory is currently disabled by the user.")
        }

        if (_isPrivateModeActive.value) {
            return PrivacyCheckResult.Denied("Private Mode is active: memories will not be stored permanently.")
        }

        if (containsSensitiveSecret(content)) {
            return PrivacyCheckResult.Denied("Security check failed: SAM does not store passwords, tokens, or payment card numbers.")
        }

        if (!isExplicitlyRequested && !isUserApproved) {
            return PrivacyCheckResult.RequiresApproval("This information was inferred. Explicit user confirmation is required before saving to long-term memory.")
        }

        return PrivacyCheckResult.Allowed
    }

    fun containsSensitiveSecret(text: String): Boolean {
        for (pattern in SENSITIVE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true
            }
        }
        return false
    }
}

sealed class PrivacyCheckResult {
    object Allowed : PrivacyCheckResult()
    data class Denied(val reason: String) : PrivacyCheckResult()
    data class RequiresApproval(val prompt: String) : PrivacyCheckResult()
}
