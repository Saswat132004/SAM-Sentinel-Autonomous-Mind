package com.sam.assistant.ai.cognitive.response

import com.sam.assistant.ai.cognitive.models.CognitiveParseResult
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.voice.language.SpokenLanguage

object ResponseGenerator {

    /**
     * Generates a spoken and visual response for the cognitive parse result.
     */
    fun generateResponse(result: CognitiveParseResult): Pair<String, String> {
        val lang = result.language
        val entities = result.entities

        // 1. Clarification needed
        if (result.isClarificationNeeded && result.clarificationPrompt != null) {
            return Pair(result.clarificationPrompt, result.clarificationPrompt)
        }

        // 2. Sensitive confirmation needed
        if (result.requiresConfirmation && result.confirmationPrompt != null) {
            return Pair(result.confirmationPrompt, result.confirmationPrompt)
        }

        // 3. Structured reasoning proposal
        if (result.reasoningProposal != null) {
            val expl = result.reasoningProposal.conciseUserExplanation
            return Pair(expl, expl)
        }

        // 4. Standard intent responses
        val spoken = when (result.intent) {
            CognitiveIntent.OPEN_APP -> {
                val app = entities.appName ?: "application"
                when (lang) {
                    SpokenLanguage.HINDI -> "$app khol raha hoon."
                    SpokenLanguage.HINGLISH -> "$app open kar raha hoon."
                    SpokenLanguage.ENGLISH -> "Opening $app."
                }
            }
            CognitiveIntent.SEARCH_WEB -> {
                val q = entities.searchQuery ?: "web"
                val app = entities.appName ?: "browser"
                when (lang) {
                    SpokenLanguage.HINDI -> "$app par $q search kar raha hoon."
                    SpokenLanguage.HINGLISH -> "Searching for $q on $app."
                    SpokenLanguage.ENGLISH -> "Searching for \"$q\" using $app."
                }
            }
            CognitiveIntent.SET_ALARM -> {
                val time = entities.time ?: "the requested time"
                when (lang) {
                    SpokenLanguage.HINDI -> "$time baje ka alarm set kar diya hai."
                    SpokenLanguage.HINGLISH -> "$time baje ka alarm set kar diya hai."
                    SpokenLanguage.ENGLISH -> "Alarm set for $time."
                }
            }
            CognitiveIntent.CREATE_REMINDER -> {
                val sub = entities.subject ?: "reminder"
                val time = entities.time ?: ""
                when (lang) {
                    SpokenLanguage.HINDI -> "$time ke liye $sub ka reminder set kar diya."
                    SpokenLanguage.HINGLISH -> "$sub ka reminder $time ke liye create kar diya hai."
                    SpokenLanguage.ENGLISH -> "Reminder created for $sub at $time."
                }
            }
            CognitiveIntent.CONTROL_DEVICE -> {
                val target = entities.actionParameters["target"] ?: "device"
                val state = entities.actionParameters["state"] ?: "updated"
                when (lang) {
                    SpokenLanguage.HINDI -> "$target ko $state kar diya."
                    SpokenLanguage.HINGLISH -> "$target $state kar diya."
                    SpokenLanguage.ENGLISH -> "Turned $target $state."
                }
            }
            CognitiveIntent.GET_DEVICE_INFO -> {
                when (lang) {
                    SpokenLanguage.HINDI -> "Yahan aapki device ki jaankari hai."
                    SpokenLanguage.HINGLISH -> "Checking your device info now."
                    SpokenLanguage.ENGLISH -> "Checking device status."
                }
            }
            CognitiveIntent.GENERAL_CONVERSATION -> {
                when (lang) {
                    SpokenLanguage.HINDI -> "Namaste! Main SAM hoon, aapka personal autonomous assistant. Main aapki kya madad karoon?"
                    SpokenLanguage.HINGLISH -> "Hello! Main SAM hoon. How can I help you today?"
                    SpokenLanguage.ENGLISH -> "Hello! I am SAM, your secure offline personal assistant. What can I do for you?"
                }
            }
            CognitiveIntent.EXPLAIN_CONCEPT -> {
                val concept = entities.searchQuery ?: result.originalUtterance
                when (lang) {
                    SpokenLanguage.HINDI -> "Main $concept ke baare mein explain kar raha hoon."
                    SpokenLanguage.HINGLISH -> "Explaining $concept for you."
                    SpokenLanguage.ENGLISH -> "Here is what you need to know about $concept."
                }
            }
            CognitiveIntent.SCAN_WIFI -> "Scanning available Wi-Fi networks."
            CognitiveIntent.ANALYSE_NETWORK -> "Analysing network status and security."
            CognitiveIntent.UNKNOWN_INTENT -> {
                when (lang) {
                    SpokenLanguage.HINDI -> "Mujhe samajh nahi aaya. Kya aap thoda detail mein bata sakte hain?"
                    SpokenLanguage.HINGLISH -> "I didn't quite catch that. Could you please clarify your request?"
                    SpokenLanguage.ENGLISH -> "I couldn't understand that command. Please specify what you'd like me to do."
                }
            }
            else -> "Processing your request."
        }

        return Pair(spoken, spoken)
    }
}
