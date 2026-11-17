package com.sam.assistant.automation.core

import android.Manifest
import com.sam.assistant.automation.executor.ActionType

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class VerificationMethod {
    INTENT_ACCEPTED,
    SYSTEM_STATE_CHECK,
    USER_VISUAL_HANDOFF,
    HARDWARE_QUERY
}

data class ActionCapability(
    val actionType: ActionType,
    val displayName: String,
    val riskLevel: RiskLevel,
    val requiredPermissions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val timeoutMs: Long = 10000L,
    val verificationMethod: VerificationMethod = VerificationMethod.INTENT_ACCEPTED,
    val description: String = ""
)

object ActionRegistry {

    private val capabilities = mutableMapOf<ActionType, ActionCapability>()

    init {
        registerAll()
    }

    private fun registerAll() {
        // --- LOW RISK OPERATIONS ---
        register(
            ActionCapability(
                actionType = ActionType.LAUNCH_APP,
                displayName = "Launch Application",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 5000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Launches an installed launchable application via package manager."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.VERIFY_APP_INSTALLED,
                displayName = "Verify App Installation",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 2000L,
                verificationMethod = VerificationMethod.HARDWARE_QUERY,
                description = "Checks if an application package is installed on the device."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.LIST_APPS,
                displayName = "List Applications",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.HARDWARE_QUERY,
                description = "Lists launchable applications on the device."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SEARCH_WEB,
                displayName = "Web Search",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 6000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Constructs safe search queries and opens a web browser."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.OPEN_URL,
                displayName = "Open URL",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 6000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Opens a verified HTTPS URL in the default browser."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SEARCH_YOUTUBE,
                displayName = "YouTube Search",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 6000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Opens YouTube search for the specified topic."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.PLAY_MEDIA,
                displayName = "Play Media",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 8000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Dispatches playback request to media player or browser."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.READ_BATTERY,
                displayName = "Read Battery Level",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 2000L,
                verificationMethod = VerificationMethod.HARDWARE_QUERY,
                description = "Reads current battery percentage and charging status."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.READ_TIME_DATE,
                displayName = "Read Time & Date",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 1000L,
                verificationMethod = VerificationMethod.HARDWARE_QUERY,
                description = "Reads system time, date, and day of week."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.CONVERSATIONAL_RESPONSE,
                displayName = "Conversational Answer",
                riskLevel = RiskLevel.LOW,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.HARDWARE_QUERY,
                description = "Provides on-device voice or text response."
            )
        )

        // --- MEDIUM RISK OPERATIONS ---
        register(
            ActionCapability(
                actionType = ActionType.TOGGLE_FLASHLIGHT,
                displayName = "Toggle Flashlight",
                riskLevel = RiskLevel.MEDIUM,
                requiredPermissions = listOf(Manifest.permission.CAMERA),
                timeoutMs = 4000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Toggles rear camera torch mode via CameraManager."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.ADJUST_VOLUME,
                displayName = "Adjust Volume",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Adjusts music/media or ringer audio stream volume."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.OPEN_SETTINGS,
                displayName = "Open System Settings",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 5000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Navigates to specific system settings screens."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SET_ALARM,
                displayName = "Set Alarm",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 5000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Sets system alarm via AlarmClock intent."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.CREATE_REMINDER,
                displayName = "Create Reminder / Timer",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 5000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Schedules reminder or timer."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SAVE_MEMORY,
                displayName = "Save Personal Memory",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Stores encrypted personal memory with user consent."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.QUERY_MEMORY,
                displayName = "Query Personal Memory",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Retrieves decrypted personal memory."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.PREPARE_WHATSAPP,
                displayName = "Prepare WhatsApp Message",
                riskLevel = RiskLevel.MEDIUM,
                timeoutMs = 6000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Opens WhatsApp conversation draft with recipient."
            )
        )

        // --- HIGH RISK OPERATIONS (Mandatory Confirmation) ---
        register(
            ActionCapability(
                actionType = ActionType.MAKE_CALL,
                displayName = "Make Phone Call",
                riskLevel = RiskLevel.HIGH,
                requiredPermissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
                requiresConfirmation = true,
                timeoutMs = 8000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Places phone call after explicit affirmative confirmation."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SEND_SMS,
                displayName = "Send SMS Message",
                riskLevel = RiskLevel.HIGH,
                requiredPermissions = listOf(Manifest.permission.SEND_SMS),
                requiresConfirmation = true,
                timeoutMs = 8000L,
                verificationMethod = VerificationMethod.INTENT_ACCEPTED,
                description = "Dispatches SMS message after explicit affirmative confirmation."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.SEND_WHATSAPP,
                displayName = "Send WhatsApp Message",
                riskLevel = RiskLevel.HIGH,
                requiresConfirmation = true,
                timeoutMs = 8000L,
                verificationMethod = VerificationMethod.USER_VISUAL_HANDOFF,
                description = "Sends WhatsApp message after user review and confirmation."
            )
        )
        register(
            ActionCapability(
                actionType = ActionType.DELETE_MEMORY,
                displayName = "Delete Personal Memory",
                riskLevel = RiskLevel.HIGH,
                requiresConfirmation = true,
                timeoutMs = 3000L,
                verificationMethod = VerificationMethod.SYSTEM_STATE_CHECK,
                description = "Deletes personal memory entry after explicit confirmation."
            )
        )
    }

    fun register(capability: ActionCapability) {
        capabilities[capability.actionType] = capability
    }

    fun getCapability(actionType: ActionType): ActionCapability? = capabilities[actionType]

    fun isRegistered(actionType: ActionType): Boolean = capabilities.containsKey(actionType)

    fun isHighRisk(actionType: ActionType): Boolean =
        capabilities[actionType]?.riskLevel == RiskLevel.HIGH

    fun getAllCapabilities(): List<ActionCapability> = capabilities.values.toList()
}
