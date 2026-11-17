package com.sam.assistant.automation.core

import android.content.Context
import android.util.Log
import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.core.security.LockStateManager

sealed class ValidationOutcome {
    data object Permitted : ValidationOutcome()
    data class NeedsConfirmation(val prompt: String, val riskLevel: RiskLevel) : ValidationOutcome()
    data class MissingPermission(val permission: String, val explanation: String) : ValidationOutcome()
    data class Rejected(val reason: String, val securityThreat: Boolean = false) : ValidationOutcome()
}

class ActionValidator(private val context: Context) {

    companion object {
        private const val TAG = "ActionValidator"

        private val dangerousPatterns = listOf(
            "su", "sudo", "sh", "bash", "busybox", "chmod", "chown",
            "rm -rf", "kill -9", "/system/", "/data/data/", "/proc/",
            "eval(", "exec(", "javascript:", "content://com.android.contacts/secret",
            "screencap", "tcpdump", "keystore.dump"
        )
    }

    /**
     * Validates an ActionRequest before any system execution occurs.
     */
    fun validate(request: ActionRequest, isConfirmed: Boolean = false): ValidationOutcome {
        val actionType = request.actionType

        // 1. Lock State Check (Security Barrier)
        if (LockStateManager.isLocked.value) {
            Log.w(TAG, "Validation blocked: Device is locked.")
            return ValidationOutcome.Rejected("Device is locked. Unlock the smartphone to proceed.")
        }

        // 2. Reject Dangerous Shell / File / Surveillance Injections
        val rawToCheck = "${request.rawCommand} ${request.target} ${request.payload}".lowercase()
        for (threat in dangerousPatterns) {
            if (rawToCheck.contains(threat)) {
                Log.e(TAG, "SECURITY ALERT: Action rejected due to prohibited pattern: $threat")
                return ValidationOutcome.Rejected(
                    reason = "Prohibited system command pattern detected.",
                    securityThreat = true
                )
            }
        }

        // 3. Registry Allowlist Check
        val capability = ActionRegistry.getCapability(actionType)
            ?: return ValidationOutcome.Rejected("Action ${actionType.name} is not in the registered action allowlist.")

        // 4. Confirmation Enforcement for High-Risk Actions
        if (capability.requiresConfirmation && !isConfirmed) {
            val prompt = request.confirmationPrompt.ifBlank {
                "Are you sure you want to execute ${capability.displayName}?"
            }
            Log.d(TAG, "Confirmation required for HIGH-RISK action: ${actionType.name}")
            return ValidationOutcome.NeedsConfirmation(prompt, capability.riskLevel)
        }

        // 5. Permission Verification
        for (perm in capability.requiredPermissions) {
            when (perm) {
                android.Manifest.permission.CALL_PHONE -> {
                    // direct call requires permission, fallback to DIAL is allowed
                }
                android.Manifest.permission.CAMERA -> {
                    if (!PermissionManager.hasCameraPermission(context)) {
                        return ValidationOutcome.MissingPermission(perm, "Flashlight control requires camera permission.")
                    }
                }
                android.Manifest.permission.SEND_SMS -> {
                    // direct SMS requires permission, fallback to smsto is allowed
                }
            }
        }

        return ValidationOutcome.Permitted
    }
}
