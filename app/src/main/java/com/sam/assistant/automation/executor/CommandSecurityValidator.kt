package com.sam.assistant.automation.executor

import android.Manifest
import android.content.Context
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.core.security.LockStateManager

class CommandSecurityValidator(private val context: Context) {

    private val allowedActions = setOf(
        ActionType.VERIFY_APP_INSTALLED,
        ActionType.LAUNCH_APP,
        ActionType.LIST_APPS,
        ActionType.SEARCH_WEB,
        ActionType.OPEN_URL,
        ActionType.SEARCH_YOUTUBE,
        ActionType.PLAY_MEDIA,
        ActionType.READ_WEB_SUMMARY,
        ActionType.MAKE_CALL,
        ActionType.SEND_SMS,
        ActionType.SEND_WHATSAPP,
        ActionType.PREPARE_WHATSAPP,
        ActionType.TOGGLE_FLASHLIGHT,
        ActionType.READ_BATTERY,
        ActionType.READ_TIME_DATE,
        ActionType.OPEN_SETTINGS,
        ActionType.ADJUST_VOLUME,
        ActionType.SET_ALARM,
        ActionType.CREATE_REMINDER,
        ActionType.SAVE_MEMORY,
        ActionType.DELETE_MEMORY,
        ActionType.QUERY_MEMORY,
        ActionType.ASK_CLARIFICATION,
        ActionType.CONFIRM_ACTION,
        ActionType.CANCEL_ACTION,
        ActionType.CONVERSATIONAL_RESPONSE
    )

    fun validate(request: ActionRequest): ValidationResult {
        // 1. Lock-state check: No automation allowed while locked
        if (LockStateManager.isLocked.value) {
            return ValidationResult.Blocked("Device is locked. Unlocking the smartphone is required to execute commands.")
        }

        // 2. Allowlist check
        if (!allowedActions.contains(request.actionType)) {
            return ValidationResult.Blocked("Action ${request.actionType} is not in the authorized action allowlist.")
        }

        // 3. Permission checks
        when (request.actionType) {
            ActionType.MAKE_CALL -> {
                if (!PermissionManager.hasCallPhonePermission(context)) {
                    return ValidationResult.MissingPermission(
                        Manifest.permission.CALL_PHONE,
                        "Calling contacts requires phone call permission."
                    )
                }
                if (!PermissionManager.hasContactsPermission(context)) {
                    return ValidationResult.MissingPermission(
                        Manifest.permission.READ_CONTACTS,
                        "Looking up contacts requires contacts access."
                    )
                }
            }
            ActionType.SEND_SMS -> {
                if (!PermissionManager.hasSmsPermission(context)) {
                    return ValidationResult.MissingPermission(
                        Manifest.permission.SEND_SMS,
                        "Sending SMS requires SMS permission."
                    )
                }
            }
            ActionType.TOGGLE_FLASHLIGHT -> {
                if (!PermissionManager.hasCameraPermission(context)) {
                    return ValidationResult.MissingPermission(
                        Manifest.permission.CAMERA,
                        "Flashlight control requires camera permission."
                    )
                }
            }
            else -> {}
        }

        // 4. Confirmation requirement for sensitive operations
        if (request.actionType.isSensitive && request.requiresConfirmation) {
            val prompt = when (request.actionType) {
                ActionType.MAKE_CALL -> "Are you sure you want to call ${request.target}?"
                ActionType.SEND_SMS -> "Ready to send SMS to ${request.target}: \"${request.payload}\". Should I send it?"
                ActionType.SEND_WHATSAPP -> "Ready to send WhatsApp to ${request.target}: \"${request.payload}\". Should I proceed?"
                ActionType.DELETE_MEMORY -> "Are you sure you want to delete this memory?"
                else -> "Please confirm you want to proceed with ${request.actionType.name}."
            }
            return ValidationResult.RequiresConfirmation(prompt)
        }

        return ValidationResult.Approved
    }
}

sealed class ValidationResult {
    object Approved : ValidationResult()
    data class RequiresConfirmation(val prompt: String) : ValidationResult()
    data class MissingPermission(val permission: String, val explanation: String) : ValidationResult()
    data class Blocked(val reason: String) : ValidationResult()
}

