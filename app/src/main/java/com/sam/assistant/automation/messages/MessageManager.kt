package com.sam.assistant.automation.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.sam.assistant.automation.calls.CallManager
import com.sam.assistant.automation.contacts.ContactResolver
import com.sam.assistant.automation.core.ResultVerifier
import com.sam.assistant.automation.executor.ExecutionResult
import com.sam.assistant.core.permissions.PermissionManager
import java.net.URLEncoder

class MessageManager(
    private val context: Context,
    private val callManager: CallManager,
    private val resultVerifier: ResultVerifier = ResultVerifier(context)
) {

    companion object {
        private const val TAG = "MessageManager"
    }

    fun sendSms(contactOrNumber: String, message: String, confirmed: Boolean): ExecutionResult {
        if (!confirmed) {
            Log.w(TAG, "SMS dispatch rejected: Confirmation required.")
            return ExecutionResult.Error("SMS action requires explicit user confirmation.")
        }

        val cleanedTarget = contactOrNumber.trim()
        val isDigits = cleanedTarget.matches(Regex("^[+]?[0-9\\s\\-()]+$"))

        val targetNumber: String
        val targetName: String

        if (isDigits) {
            targetNumber = cleanedTarget.replace(Regex("[^0-9+]"), "")
            targetName = ContactResolver.maskPhoneNumber(targetNumber)
        } else {
            val contact = callManager.findContactByName(cleanedTarget)
            if (contact != null) {
                targetNumber = contact.number.replace(Regex("[^0-9+]"), "")
                targetName = contact.name
            } else {
                return com.sam.assistant.automation.executor.ErrorRecoveryManager.contactNotFound(cleanedTarget)
            }
        }

        return try {
            val hasSmsPermission = PermissionManager.hasSmsPermission(context)
            if (hasSmsPermission) {
                val smsManager = context.getSystemService(SmsManager::class.java)
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null)

                val report = resultVerifier.verifyMessageDispatch("SMS_DIRECT", targetName)
                ExecutionResult.Success(
                    message = report.details,
                    spokenResponse = "SMS successfully submitted to network for $targetName."
                )
            } else {
                // Fallback to SMS App intent
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$targetNumber")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                val report = resultVerifier.verifyMessageDispatch("SMS_HANDOFF", targetName)
                ExecutionResult.Success(
                    message = report.details,
                    spokenResponse = "Prepared SMS to $targetName in your messages app."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS dispatch failed", e)
            com.sam.assistant.automation.executor.ErrorRecoveryManager.genericFailure(
                component = "MessageManager",
                technicalError = "SMS dispatch failed: ${e.message}",
                userExplanation = "Could not send SMS to $targetName."
            )
        }
    }

    fun prepareWhatsApp(contactOrNumber: String, message: String): ExecutionResult {
        val cleanedTarget = contactOrNumber.trim()
        val isDigits = cleanedTarget.matches(Regex("^[+]?[0-9\\s\\-()]+$"))

        val targetNumber: String
        val targetName: String

        if (isDigits) {
            targetNumber = cleanedTarget.replace(Regex("[^0-9]"), "")
            targetName = ContactResolver.maskPhoneNumber(targetNumber)
        } else {
            val contact = callManager.findContactByName(cleanedTarget)
            if (contact != null) {
                targetNumber = contact.number.replace(Regex("[^0-9]"), "")
                targetName = contact.name
            } else {
                return com.sam.assistant.automation.executor.ErrorRecoveryManager.contactNotFound(cleanedTarget)
            }
        }

        return try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$targetNumber&text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val report = resultVerifier.verifyMessageDispatch("WHATSAPP_HANDOFF", targetName)
            ExecutionResult.Success(
                message = report.details,
                spokenResponse = "Opening WhatsApp conversation with $targetName."
            )
        } catch (e: Exception) {
            // General share fallback
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                ExecutionResult.Success(
                    message = "Opened WhatsApp message composer.",
                    spokenResponse = "Opening WhatsApp with your message."
                )
            } catch (ex: Exception) {
                Log.e(TAG, "WhatsApp not installed or intent failed", ex)
                com.sam.assistant.automation.executor.ErrorRecoveryManager.appNotInstalled("WhatsApp")
            }
        }
    }
}
