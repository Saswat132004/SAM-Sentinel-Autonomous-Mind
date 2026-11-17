package com.sam.assistant.automation.executor

data class ComponentError(
    val component: String,
    val technicalMessage: String,
    val exceptionType: String? = null,
    val userExplanation: String,
    val suggestedAlternative: String? = null,
    val isRecoverable: Boolean = false
) {
    fun toExecutionResult(): ExecutionResult.Failed {
        val fullUserMessage = if (suggestedAlternative != null) {
            "$userExplanation $suggestedAlternative"
        } else {
            userExplanation
        }
        return ExecutionResult.Failed(
            userExplanation = fullUserMessage,
            technicalError = "[$component] $technicalMessage" + (exceptionType?.let { " ($it)" } ?: ""),
            failingComponent = component,
            suggestedAlternative = suggestedAlternative,
            spokenError = fullUserMessage
        )
    }
}

object ErrorRecoveryManager {

    fun appNotInstalled(appName: String, fallbackAlternative: String? = null): ExecutionResult.Failed {
        val alternative = when {
            appName.contains("brave", ignoreCase = true) -> "Would you like me to open Chrome instead?"
            appName.contains("spotify", ignoreCase = true) -> "Would you like me to open YouTube Music instead?"
            appName.contains("chrome", ignoreCase = true) -> "Would you like me to use the default web browser?"
            fallbackAlternative != null -> fallbackAlternative
            else -> "You can install $appName from the Google Play Store."
        }
        return ComponentError(
            component = "AppLauncher",
            technicalMessage = "Package for app \"$appName\" is not installed on this device.",
            userExplanation = "$appName is not installed on this smartphone.",
            suggestedAlternative = alternative,
            isRecoverable = true
        ).toExecutionResult()
    }

    fun contactNotFound(contactName: String): ExecutionResult.Failed {
        return ComponentError(
            component = "CallManager",
            technicalMessage = "No contact matching query \"$contactName\" found in ContactsProvider.",
            userExplanation = "I could not find \"$contactName\" in your contacts.",
            suggestedAlternative = "Please verify the contact name or check your Contacts app.",
            isRecoverable = false
        ).toExecutionResult()
    }

    fun missingPermission(permission: String, humanReadableName: String): ExecutionResult.PermissionRequired {
        return ExecutionResult.PermissionRequired(
            permission = permission,
            userExplanation = "SAM requires $humanReadableName permission to perform this action. You can grant it in the Permissions Setup screen."
        )
    }

    fun lockStateBlocked(actionDescription: String): ExecutionResult.Failed {
        return ComponentError(
            component = "LockStateGuard",
            technicalMessage = "Security Barrier: Command \"$actionDescription\" blocked because smartphone screen is locked.",
            userExplanation = "Your smartphone is locked. Please unlock the device to execute this command.",
            suggestedAlternative = "Unlock your phone with your fingerprint or PIN and say the command again.",
            isRecoverable = true
        ).toExecutionResult()
    }

    fun mediaPlaybackNotice(appName: String, destinationUrl: String): ExecutionResult.Success {
        return ExecutionResult.Success(
            message = "Opened $appName with media destination: $destinationUrl. Due to browser autoplay policies, tap play to start listening.",
            spokenResponse = "Opened $appName. Please tap play to begin playback."
        )
    }

    fun genericFailure(
        component: String,
        technicalError: String,
        userExplanation: String,
        suggestedAlternative: String? = null
    ): ExecutionResult.Failed {
        return ComponentError(
            component = component,
            technicalMessage = technicalError,
            userExplanation = userExplanation,
            suggestedAlternative = suggestedAlternative,
            isRecoverable = suggestedAlternative != null
        ).toExecutionResult()
    }
}
