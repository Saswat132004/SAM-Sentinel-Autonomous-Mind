package com.sam.assistant.automation.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sam.assistant.automation.contacts.ContactResolver
import com.sam.assistant.automation.core.ResultVerifier
import com.sam.assistant.automation.executor.ExecutionResult
import com.sam.assistant.core.permissions.PermissionManager

data class ContactMatch(
    val name: String,
    val number: String
)

class CallManager(
    private val context: Context,
    private val contactResolver: ContactResolver = ContactResolver(context),
    private val resultVerifier: ResultVerifier = ResultVerifier(context)
) {

    companion object {
        private const val TAG = "CallManager"
    }

    fun findContactByName(contactName: String): ContactMatch? {
        val entry = contactResolver.findSingleContact(contactName)
        return if (entry != null) {
            ContactMatch(entry.displayName, entry.phoneNumber)
        } else {
            val matches = contactResolver.findContacts(contactName)
            matches.firstOrNull()?.let { ContactMatch(it.displayName, it.phoneNumber) }
        }
    }

    fun initiateCall(phoneNumberOrName: String, confirmed: Boolean): ExecutionResult {
        // High-Risk Enforcement: Strict confirmation required
        if (!confirmed) {
            Log.w(TAG, "Call rejected: User has not explicitly confirmed.")
            return ExecutionResult.Error("Call action requires explicit user confirmation.")
        }

        val cleanedTarget = phoneNumberOrName.trim()
        val isDigits = cleanedTarget.matches(Regex("^[+]?[0-9\\s\\-()]+$"))

        val targetNumber: String
        val targetDisplayName: String

        if (isDigits) {
            targetNumber = cleanedTarget.replace(Regex("[^0-9+]"), "")
            targetDisplayName = ContactResolver.maskPhoneNumber(targetNumber)
        } else {
            val contact = findContactByName(cleanedTarget)
            if (contact != null) {
                targetNumber = contact.number.replace(Regex("[^0-9+]"), "")
                targetDisplayName = contact.name
            } else {
                return com.sam.assistant.automation.executor.ErrorRecoveryManager.contactNotFound(cleanedTarget)
            }
        }

        return try {
            val hasCallPermission = PermissionManager.hasCallPhonePermission(context)
            val callIntent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(callIntent)

            val report = resultVerifier.verifyCallDispatch(
                directCall = hasCallPermission,
                displayName = targetDisplayName
            )
            Log.d(TAG, "Call initiated successfully: ${report.details}")

            val spokenMsg = if (hasCallPermission) "Calling $targetDisplayName now." else "Opening dialer for $targetDisplayName."
            ExecutionResult.Success(
                message = report.details,
                spokenResponse = spokenMsg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Call execution failed", e)
            com.sam.assistant.automation.executor.ErrorRecoveryManager.genericFailure(
                component = "CallManager",
                technicalError = "Call intent failed: ${e.message}",
                userExplanation = "Failed to place call to $targetDisplayName."
            )
        }
    }
}
