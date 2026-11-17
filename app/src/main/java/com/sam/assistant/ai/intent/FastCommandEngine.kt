package com.sam.assistant.ai.intent

import com.sam.assistant.automation.executor.ActionRequest
import com.sam.assistant.automation.executor.ActionType
import java.util.regex.Pattern

data class ParsedIntent(
    val actionType: ActionType,
    val target: String = "",
    val payload: String = "",
    val extraParams: Map<String, String> = emptyMap(),
    val rawCommand: String = "",
    val requiresConfirmation: Boolean = actionType.isSensitive,
    val confirmationPrompt: String = ""
) {
    fun toActionRequest(): ActionRequest = ActionRequest(
        actionType = actionType,
        target = target,
        payload = payload,
        extraParams = extraParams,
        rawCommand = rawCommand,
        requiresConfirmation = requiresConfirmation,
        confirmationPrompt = confirmationPrompt
    )
}

class FastCommandEngine {

    fun parseCommand(input: String): ActionRequest? {
        val parsed = parseIntent(input)
        return parsed?.toActionRequest()
    }

    fun parseIntent(input: String): ParsedIntent? {
        val raw = input.trim()
        val normalized = normalizeCommand(raw)

        // 0. Active Confirmation check (Yes / No / Cancel / Haan / Nahi)
        if (SessionContextManager.hasPendingConfirmation()) {
            if (SessionContextManager.isAffirmative(normalized)) {
                return ParsedIntent(
                    actionType = ActionType.CONFIRM_ACTION,
                    rawCommand = raw
                )
            }
            if (SessionContextManager.isNegative(normalized)) {
                return ParsedIntent(
                    actionType = ActionType.CANCEL_ACTION,
                    rawCommand = raw
                )
            }
        }

        // 0b. Active Clarification check
        val pendingClarification = SessionContextManager.getPendingClarification()
        if (pendingClarification != null) {
            val choice = SessionContextManager.resolveOption(normalized, pendingClarification.options)
            if (choice != null) {
                return ParsedIntent(
                    actionType = ActionType.PLAY_MEDIA,
                    target = choice.lowercase(),
                    payload = "",
                    rawCommand = raw
                )
            }
        }

        // 1. Browser Web Search
        // Examples:
        // "open brave and search for the latest cybersecurity news"
        // "open brave and search for machine learning courses"
        // "open chrome, search for the weather and read the result"
        // "search for [query] on brave / chrome"
        // "brave kholo aur machine learning courses search karo"
        val searchWithAppPattern = Pattern.compile(
            "^(?:open|launch|kholo)?\\s*(brave|chrome|google chrome|browser)(?:\\s+browser)?(?:\\s+kholo|\\s+khol)?[,\\s]*(?:and|then|aur|se)?[,\\s]*(?:search\\s+for|search|khojo)[,\\s]+(.+?)(?:[,\\s]+(?:and|then|aur)[,\\s]+(?:read the result|read it))?$"
        )
        val searchMatcher = searchWithAppPattern.matcher(normalized)
        if (searchMatcher.matches()) {
            val browser = searchMatcher.group(1)?.trim() ?: "browser"
            var query = searchMatcher.group(2)?.trim() ?: ""
            // Clean trailing instructions if any
            query = query.replace(Regex("(?i)[,\\s]*(?:and|then)\\s*read\\s+(?:the\\s+)?result.*$"), "").trim()
            query = query.replace(Regex("(?i)\\s*(?:karo|khojo)$"), "").trim()
            query = query.replace(Regex("(?i)^(?:for\\s+)?(?:the\\s+)?"), "").trim()
            return ParsedIntent(
                actionType = ActionType.SEARCH_WEB,
                target = browser,
                payload = query,
                rawCommand = raw
            )
        }

        // "search for [query] on/using [browser]"
        val searchOnBrowserPattern = Pattern.compile("^(?:search\\s+for|search|khojo)\\s+(.+?)\\s+(?:on|in|using|via)\\s+(brave|chrome|browser)$")
        val searchOnMatcher = searchOnBrowserPattern.matcher(normalized)
        if (searchOnMatcher.matches()) {
            var query = searchOnMatcher.group(1)?.trim() ?: ""
            val browser = searchOnMatcher.group(2)?.trim() ?: "browser"
            query = query.replace(Regex("(?i)^(?:for\\s+)?(?:the\\s+)?"), "").trim()
            return ParsedIntent(
                actionType = ActionType.SEARCH_WEB,
                target = browser,
                payload = query,
                rawCommand = raw
            )
        }

        // 2. YouTube Search & Play
        // Examples:
        // "open youtube, search for python tutorials and play the first suitable result"
        // "open youtube and search for cybersecurity tutorials"
        // "search for python tutorials on youtube"
        // "youtube kholo aur python tutorials search karo"
        val ytSearchPattern1 = Pattern.compile(
            "^(?:open|launch|kholo)?\\s*youtube(?:\\s+kholo|\\s+khol)?[,\\s]*(?:and|then|aur)?[,\\s]*(?:search for|search|khojo)[,\\s]+(.+?)(?:[,\\s]+(?:and|then|aur)?[,\\s]*(?:play the first suitable result|play first result|play it|chalao|bajao))?$"
        )
        val ytMatcher1 = ytSearchPattern1.matcher(normalized)
        if (ytMatcher1.matches()) {
            var query = ytMatcher1.group(1)?.trim() ?: ""
            query = query.replace(Regex("(?i)[,\\s]*(?:and|then|aur)?\\s*play\\s+.*$"), "").trim()
            query = query.replace(Regex("(?i)\\s*(?:karo|khojo)$"), "").trim()
            if (query.startsWith("the ")) {
                query = query.removePrefix("the ").trim()
            }
            return ParsedIntent(
                actionType = ActionType.SEARCH_YOUTUBE,
                target = "youtube",
                payload = query,
                rawCommand = raw
            )
        }

        val ytSearchPattern2 = Pattern.compile("^(?:search for|search|khojo)\\s+(.+?)\\s+(?:on|in)\\s+youtube$")
        val ytMatcher2 = ytSearchPattern2.matcher(normalized)
        if (ytMatcher2.matches()) {
            var query = ytMatcher2.group(1)?.trim() ?: ""
            if (query.startsWith("the ")) {
                query = query.removePrefix("the ").trim()
            }
            return ParsedIntent(
                actionType = ActionType.SEARCH_YOUTUBE,
                target = "youtube",
                payload = query,
                rawCommand = raw
            )
        }

        // 3. Media Playback
        // Examples:
        // "play music from brave browser"
        // "play some music using brave browser"
        // "play music on spotify"
        // "play music on youtube"
        // "play music" -> ambiguous (needs clarification)
        // "gaana bajao", "sangeet chalao"
        val playMediaPattern = Pattern.compile("^(?:play|chalao|bajao)\\s+(?:some\\s+)?(?:music|songs?|tracks?|gaana|sangeet)(?:\\s+(?:from|using|on|in|se|via)\\s+([a-z0-9\\s]+))?$")
        val playMatcher = playMediaPattern.matcher(normalized)
        if (playMatcher.matches()) {
            val requestedApp = playMatcher.group(1)?.trim()
            return if (!requestedApp.isNullOrBlank()) {
                val cleanedApp = when {
                    requestedApp.contains("brave") -> "brave"
                    requestedApp.contains("spotify") -> "spotify"
                    requestedApp.contains("youtube music") || requestedApp.contains("yt music") -> "youtube music"
                    requestedApp.contains("youtube") -> "youtube"
                    requestedApp.contains("chrome") -> "chrome"
                    else -> requestedApp
                }
                ParsedIntent(
                    actionType = ActionType.PLAY_MEDIA,
                    target = cleanedApp,
                    payload = "",
                    rawCommand = raw
                )
            } else {
                // Check if user has a remembered preference
                val remembered = SessionContextManager.getPreferredMusicSource()
                if (remembered != null) {
                    ParsedIntent(
                        actionType = ActionType.PLAY_MEDIA,
                        target = remembered,
                        payload = "",
                        rawCommand = raw
                    )
                } else {
                    // Ambiguous! Ask for clarification
                    ParsedIntent(
                        actionType = ActionType.ASK_CLARIFICATION,
                        target = "music_source",
                        payload = "Should I use YouTube, Spotify or Brave?",
                        rawCommand = raw
                    )
                }
            }
        }

        // 4. WhatsApp: Messaging / Preparing message
        // Examples:
        // "whatsapp kholo aur rahul ko message bhejne ke liye prepare karo"
        // "open whatsapp and prepare a message for rahul"
        // "prepare whatsapp message for rahul"
        // "send whatsapp to rahul saying [message]"
        val waPreparePattern = Pattern.compile(
            "^(?:open|launch)?\\s*whatsapp(?:\\s+kholo|\\s+khol)?[,\\s]*(?:and|then|aur)?[,\\s]*([a-z0-9\\s+]+?)\\s*ko\\s+message\\s+(?:bhejne\\s+ke\\s+liye\\s+prepare\\s+karo|bhejo)$"
        )
        val waPrepMatcher = waPreparePattern.matcher(normalized)
        if (waPrepMatcher.matches()) {
            val contact = waPrepMatcher.group(1)?.trim() ?: ""
            return ParsedIntent(
                actionType = ActionType.PREPARE_WHATSAPP,
                target = contact,
                rawCommand = raw
            )
        }

        val waPreparePatternEn = Pattern.compile(
            "^(?:open\\s+whatsapp\\s+and\\s+)?prepare\\s+(?:a\\s+)?(?:whatsapp\\s+)?message\\s+for\\s+([a-z0-9\\s]+)$"
        )
        val waPrepEnMatcher = waPreparePatternEn.matcher(normalized)
        if (waPrepEnMatcher.matches()) {
            val contact = waPrepEnMatcher.group(1)?.trim() ?: ""
            return ParsedIntent(
                actionType = ActionType.PREPARE_WHATSAPP,
                target = contact,
                rawCommand = raw
            )
        }

        val waSendPattern = Pattern.compile("^(?:send\\s+)?whatsapp(?:\\s+message)?\\s+to\\s+(.+?)\\s+saying\\s+(.+)$")
        val waSendMatcher = waSendPattern.matcher(normalized)
        if (waSendMatcher.matches()) {
            val contact = waSendMatcher.group(1)?.trim() ?: ""
            val msg = waSendMatcher.group(2)?.trim() ?: ""
            return ParsedIntent(
                actionType = ActionType.SEND_WHATSAPP,
                target = contact,
                payload = msg,
                rawCommand = raw,
                requiresConfirmation = true,
                confirmationPrompt = "Send WhatsApp to $contact: \"$msg\"?"
            )
        }

        // 5. Calling & Dialing (with confirmation safety)
        // Examples:
        // "call rahul and confirm before calling"
        // "call rahul" / "dial rahul"
        // "rahul ko call lagao" / "rahul ko phone karo" / "rahul ko call karo"
        val callHinglishPattern = Pattern.compile("^([a-z0-9\\s+]+?)\\s+ko\\s+(?:call|phone)\\s+(?:lagao|karo|milao)$")
        val callHinglishMatcher = callHinglishPattern.matcher(normalized)
        if (callHinglishMatcher.matches()) {
            val contact = callHinglishMatcher.group(1)?.trim() ?: ""
            val displayName = contact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            return ParsedIntent(
                actionType = ActionType.MAKE_CALL,
                target = contact,
                rawCommand = raw,
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want to call $displayName?"
            )
        }

        var callCleaned = normalized
        val hasConfirmPrompt = callCleaned.contains("confirm before calling") || callCleaned.contains("confirm")
        callCleaned = callCleaned.replace(Regex("(?i)[,\\s]*(?:and|please)?[,\\s]*confirm(?:\\s+before\\s+calling)?$"), "").trim()

        val callEnPattern = Pattern.compile("^(?:call|dial)\\s+([a-z0-9\\s+]+)$")
        val callEnMatcher = callEnPattern.matcher(callCleaned)
        if (callEnMatcher.matches()) {
            val contact = callEnMatcher.group(1)?.trim() ?: ""
            val displayName = contact.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            return ParsedIntent(
                actionType = ActionType.MAKE_CALL,
                target = contact,
                rawCommand = raw,
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want to call $displayName?"
            )
        }

        // 6. SMS Messaging
        val smsPattern1 = Pattern.compile("^(?:send\\s+(?:an?\\s+)?sms|send\\s+(?:a\\s+)?message)\\s+to\\s+(.+?)\\s+saying\\s+(.+)$")
        val smsMatcher1 = smsPattern1.matcher(normalized)
        if (smsMatcher1.matches()) {
            val contact = smsMatcher1.group(1)?.trim() ?: ""
            val msg = smsMatcher1.group(2)?.trim() ?: ""
            return ParsedIntent(
                actionType = ActionType.SEND_SMS,
                target = contact,
                payload = msg,
                rawCommand = raw,
                requiresConfirmation = true,
                confirmationPrompt = "Send SMS to $contact: \"$msg\"?"
            )
        }

        val smsPattern2 = Pattern.compile("^(?:text|message)\\s+(.+?)\\s+(?:that|saying)\\s+(.+)$")
        val smsMatcher2 = smsPattern2.matcher(normalized)
        if (smsMatcher2.matches()) {
            val contact = smsMatcher2.group(1)?.trim() ?: ""
            val msg = smsMatcher2.group(2)?.trim() ?: ""
            return ParsedIntent(
                actionType = ActionType.SEND_SMS,
                target = contact,
                payload = msg,
                rawCommand = raw,
                requiresConfirmation = true,
                confirmationPrompt = "Send SMS to $contact: \"$msg\"?"
            )
        }

        // 7. Flashlight / Torch
        // "turn on flashlight", "torch on", "torch jalao", "torch band karo", "flashlight band karo"
        if (normalized.contains("flashlight") || normalized.contains("torch")) {
            val isOff = normalized.contains("off") || normalized.contains("disable") ||
                    normalized.contains("band") || normalized.contains("bujhao")
            val state = if (isOff) "off" else "on"
            return ParsedIntent(
                actionType = ActionType.TOGGLE_FLASHLIGHT,
                target = state,
                rawCommand = raw
            )
        }

        // 8. Battery Status
        // "battery status", "check battery", "how much battery", "battery kitni hai", "battery check karo"
        if (normalized.contains("battery")) {
            return ParsedIntent(
                actionType = ActionType.READ_BATTERY,
                rawCommand = raw
            )
        }

        // 9. Time and Date
        // "what time is it", "current time", "kitne baje hain", "time kya hua hai", "today's date"
        if (normalized.contains("time") || normalized.contains("date") ||
            normalized.contains("baje") || normalized.contains("samay") || normalized.contains("day today")
        ) {
            if (!normalized.contains("alarm") && !normalized.contains("remind") && !normalized.contains("timer")) {
                return ParsedIntent(
                    actionType = ActionType.READ_TIME_DATE,
                    rawCommand = raw
                )
            }
        }

        // 10. Alarms & Timers
        // Examples:
        // "set alarm for 7:30 am", "alarm lagao 7 baje", "subah saat baje ka alarm lagao", "kal subah saat baje alarm laga do"
        // "mujhe ek timer lagana hai", "timer lagao 5 minute ka"
        val hindiNumberMap = mapOf(
            "ek" to 1, "do" to 2, "teen" to 3, "chaar" to 4, "paanch" to 5,
            "chhe" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
            "gyarah" to 11, "baarah" to 12
        )

        // Hindi Alarm pattern: "kal subah saat baje alarm laga do", "subah saat baje ka alarm lagao"
        val hindiAlarmPattern = Pattern.compile("^(?:kal\\s+)?(?:(subah|shaam|raat|dopahar)\\s+)?([a-z0-9]+)\\s+baje(?:\\s+ka)?\\s+alarm(?:\\s+(?:laga\\s+do|lagao|set\\s+karo))?$")
        val hindiAlarmMatcher = hindiAlarmPattern.matcher(normalized)
        if (hindiAlarmMatcher.matches()) {
            val period = hindiAlarmMatcher.group(1) ?: "subah"
            val hourStr = hindiAlarmMatcher.group(2) ?: "7"
            var hour = hindiNumberMap[hourStr] ?: hourStr.toIntOrNull() ?: 7

            if ((period == "shaam" || period == "raat") && hour < 12) hour += 12
            if (period == "dopahar" && hour < 12 && hour != 12) hour += 12

            return ParsedIntent(
                actionType = ActionType.SET_ALARM,
                rawCommand = raw,
                extraParams = mapOf(
                    "hour" to hour.toString(),
                    "minute" to "0"
                )
            )
        }

        val alarmPattern = Pattern.compile("^(?:set(?:\\s+an?)?\\s+alarm\\s+(?:for|at)\\s+)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$")
        val alarmMatcher = alarmPattern.matcher(normalized)
        if (alarmMatcher.matches()) {
            var hour = alarmMatcher.group(1)?.toIntOrNull() ?: 7
            val min = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatcher.group(3)

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            return ParsedIntent(
                actionType = ActionType.SET_ALARM,
                rawCommand = raw,
                extraParams = mapOf(
                    "hour" to hour.toString(),
                    "minute" to min.toString()
                )
            )
        }

        // Timer patterns: "mujhe ek timer lagana hai", "timer lagao 5 minute ka", "set timer for 10 minutes"
        val timerPattern = Pattern.compile("^(?:mujhe\\s+ek\\s+timer\\s+lagana\\s+hai|timer\\s+lagao(?:\\s+(\\d+)\\s+minutes?)?(?:\\s+ka)?|set\\s+(?:a\\s+)?timer(?:\\s+for\\s+(\\d+)\\s+minutes?)?)$")
        val timerMatcher = timerPattern.matcher(normalized)
        if (timerMatcher.matches() || normalized.contains("timer lagana hai")) {
            val minutes = timerMatcher.group(1) ?: timerMatcher.group(2) ?: "5"
            return ParsedIntent(
                actionType = ActionType.CREATE_REMINDER,
                payload = "Timer",
                rawCommand = raw,
                extraParams = mapOf("delayMinutes" to minutes)
            )
        }

        // 11. Reminders
        val reminderPattern = Pattern.compile("^(?:remind me to|create reminder to)\\s+(.+?)\\s+in\\s+(\\d+)\\s+minutes?$")
        val reminderMatcher = reminderPattern.matcher(normalized)
        if (reminderMatcher.matches()) {
            val task = reminderMatcher.group(1)?.trim() ?: ""
            val minutes = reminderMatcher.group(2)?.trim() ?: "10"
            return ParsedIntent(
                actionType = ActionType.CREATE_REMINDER,
                payload = task,
                rawCommand = raw,
                extraParams = mapOf("delayMinutes" to minutes)
            )
        }

        // 12. Volume
        if (normalized.contains("volume") || normalized == "mute" || normalized == "unmute" || normalized.contains("aawaz")) {
            val dir = when {
                normalized.contains("up") || normalized.contains("raise") || normalized.contains("increase") || normalized.contains("badhao") -> "up"
                normalized.contains("down") || normalized.contains("lower") || normalized.contains("decrease") || normalized.contains("kam karo") -> "down"
                normalized.contains("mute") || normalized.contains("chup") -> "mute"
                normalized.contains("unmute") -> "unmute"
                else -> "same"
            }
            return ParsedIntent(
                actionType = ActionType.ADJUST_VOLUME,
                target = dir,
                rawCommand = raw
            )
        }

        // 13. System Settings
        if (normalized.contains("settings")) {
            val targetSetting = when {
                normalized.contains("wifi") || normalized.contains("wi-fi") -> "wifi"
                normalized.contains("bluetooth") -> "bluetooth"
                normalized.contains("display") || normalized.contains("brightness") -> "display"
                normalized.contains("sound") || normalized.contains("audio") -> "sound"
                normalized.contains("battery") -> "battery"
                normalized.contains("storage") -> "storage"
                else -> "general"
            }
            return ParsedIntent(
                actionType = ActionType.OPEN_SETTINGS,
                target = targetSetting,
                rawCommand = raw
            )
        }

        // 14. App Launch
        // "open brave", "launch youtube", "chrome kholo", "open whatsapp and show my recent chats"
        val openAppWithFollowupPattern = Pattern.compile("^(?:open|launch|start)\\s+([a-z0-9]+)(?:\\s+(?:and|aur)\\s+(?:show|check|view|display).*)$")
        val openFollowupMatcher = openAppWithFollowupPattern.matcher(normalized)
        if (openFollowupMatcher.matches()) {
            val appName = openFollowupMatcher.group(1)?.trim() ?: ""
            if (appName.isNotBlank() && appName != "settings") {
                return ParsedIntent(
                    actionType = ActionType.LAUNCH_APP,
                    target = appName,
                    rawCommand = raw
                )
            }
        }

        val openAppPattern1 = Pattern.compile("^(?:open|launch|start)\\s+([a-z0-9\\s]+)$")
        val openAppMatcher1 = openAppPattern1.matcher(normalized)
        if (openAppMatcher1.matches()) {
            val appName = openAppMatcher1.group(1)?.trim() ?: ""
            if (appName != "settings" && !appName.contains("flashlight") && !appName.contains("torch") && !appName.contains("music")) {
                return ParsedIntent(
                    actionType = ActionType.LAUNCH_APP,
                    target = appName,
                    rawCommand = raw
                )
            }
        }

        val openAppPatternHinglish = Pattern.compile("^([a-z0-9\\s]+)\\s+(?:kholo|khol|chalao)$")
        val openAppMatcherH = openAppPatternHinglish.matcher(normalized)
        if (openAppMatcherH.matches()) {
            val appName = openAppMatcherH.group(1)?.trim() ?: ""
            if (appName != "settings" && !appName.contains("flashlight") && !appName.contains("torch") && !appName.contains("music") && !appName.contains("gaana")) {
                return ParsedIntent(
                    actionType = ActionType.LAUNCH_APP,
                    target = appName,
                    rawCommand = raw
                )
            }
        }

        // 15. Memory Operations
        if (normalized.startsWith("remember") || normalized.startsWith("forget") ||
            normalized.contains("what do you remember") || normalized.contains("what did i tell you") ||
            normalized.contains("what have i saved")
        ) {
            val action = if (normalized.startsWith("forget")) {
                ActionType.DELETE_MEMORY
            } else if (normalized.startsWith("remember")) {
                ActionType.SAVE_MEMORY
            } else {
                ActionType.QUERY_MEMORY
            }
            return ParsedIntent(
                actionType = action,
                rawCommand = raw
            )
        }

        return null
    }

    private fun normalizeCommand(input: String): String {
        var str = input.trim().lowercase()

        // 1. Remove polite conversational prefixes and greetings (allowing commas/spaces)
        val prefixRegex = Regex("^(?:hey\\s+sam|sam|ok\\s+sam|okay\\s+sam|do\\s+one\\s+thing|could\\s+you\\s+please|can\\s+you\\s+please|can\\s+you|please\\s+kindly|kindly|please|i\\s+want\\s+to|i'd\\s+like\\s+to)[,\\s]+")
        str = str.replace(prefixRegex, "").trim()
        str = str.replace(prefixRegex, "").trim()

        // 2. Remove polite trailing markers (e.g. "please", "na", "bhai")
        str = str.replace(Regex("(?i)[,\\s]+(?:please|na|bhai|yaar)$"), "")

        return str.trim()
    }
}
