package com.sam.assistant.ai.cognitive.intent

import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.ai.cognitive.models.IntentClassificationResult
import com.sam.assistant.ai.cognitive.normalizer.LanguageNormalizer
import java.util.regex.Pattern

object IntentClassifier {

    /**
     * Classifies user utterance into a CognitiveIntent with confidence scoring.
     */
    fun classify(rawUtterance: String): IntentClassificationResult {
        val normalized = LanguageNormalizer.normalize(rawUtterance)
        if (normalized.isBlank()) {
            return IntentClassificationResult(
                intent = CognitiveIntent.UNKNOWN_INTENT,
                confidence = 0.0f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized
            )
        }

        // 1. Follow-up commands & Ordinal references (e.g. "the second one", "doosra", "actually, make it 7 AM")
        val ordinal = LanguageNormalizer.extractOrdinal(normalized)
        if (ordinal != null && (normalized.length < 25 || normalized.contains("the ") || normalized.contains("wala"))) {
            return IntentClassificationResult(
                intent = CognitiveIntent.FOLLOW_UP_COMMAND,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "ORDINAL_REFERENCE"
            )
        }
        if (normalized.startsWith("actually") || normalized.startsWith("nahi ") || normalized.startsWith("change to") || normalized.contains("make it ")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.FOLLOW_UP_COMMAND,
                confidence = 0.93f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "CORRECTION_REFERENCE"
            )
        }

        // 2. Ambiguity check: commands without specific targets (e.g. "open that application", "call someone", "play something")
        if (normalized == "open that application" || normalized == "open that app" || normalized == "woh app kholo" || normalized == "open it") {
            return IntentClassificationResult(
                intent = CognitiveIntent.OPEN_APP,
                confidence = 0.40f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                isAmbiguous = true,
                clarificationOptions = listOf("YouTube", "Brave", "WhatsApp", "Chrome"),
                matchedPattern = "AMBIGUOUS_APP_REFERENCE"
            )
        }

        if (normalized == "call someone" || normalized == "phone karo" || normalized == "call him" || normalized == "call her") {
            return IntentClassificationResult(
                intent = CognitiveIntent.MAKE_CALL,
                confidence = 0.45f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                isAmbiguous = true,
                matchedPattern = "AMBIGUOUS_CALL_REFERENCE"
            )
        }

        // 3. Document Analysis & Summarisation
        if (normalized.contains("summarise document") || normalized.contains("summarize document") ||
            normalized.contains("summarise file") || normalized.contains("summarize this document") ||
            normalized.contains("document summarize karo")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SUMMARISE_DOCUMENT,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SUMMARISE_DOCUMENT"
            )
        }

        if (normalized.contains("read document") || normalized.contains("read pdf") || normalized.contains("document padho")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.READ_DOCUMENT,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "READ_DOCUMENT"
            )
        }

        // 4. Concept Explanation ("explain machine learning", "what is artificial intelligence", "explain whether it is suitable")
        if (normalized.startsWith("explain ") || normalized.startsWith("what is ") || normalized.startsWith("kya hai ") ||
            normalized.contains("samjhao") || normalized.contains("explain whether") || normalized.contains("explain how")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.EXPLAIN_CONCEPT,
                confidence = 0.92f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "EXPLAIN_CONCEPT"
            )
        }

        // 5. Cybersecurity & Network Analysis
        if (normalized.contains("scan wifi") || normalized.contains("scan wi-fi") || normalized.contains("wifi scan karo")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SCAN_WIFI,
                confidence = 0.96f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SCAN_WIFI"
            )
        }
        if (normalized.contains("analyse network") || normalized.contains("analyze network") ||
            normalized.contains("network security check") || normalized.contains("traffic analysis")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.ANALYSE_NETWORK,
                confidence = 0.94f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "ANALYSE_NETWORK"
            )
        }

        // 6. Alarms & Timers
        if (normalized.contains("timer")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SET_TIMER,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SET_TIMER"
            )
        }

        if (normalized.contains("alarm")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SET_ALARM,
                confidence = 0.96f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SET_ALARM"
            )
        }

        // 7. Reminders ("kal subah saat baje Python padhne ka reminder laga do", "remind me to call Mom in 10 minutes")
        if (normalized.contains("reminder") || normalized.contains("remind me") || normalized.contains("yaad dilana") || normalized.contains("yaad dilao")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.CREATE_REMINDER,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "CREATE_REMINDER"
            )
        }

        // 8. Device Info (Battery, Time, Date, Storage)
        if (normalized.contains("battery") || normalized.contains("charging") || normalized.contains("charge kitna")) {
            return IntentClassificationResult(
                intent = CognitiveIntent.GET_DEVICE_INFO,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "GET_BATTERY_INFO"
            )
        }

        if ((normalized.contains(Regex("\\btime\\b")) || normalized.contains("samay") || normalized.contains(Regex("\\bdate\\b")) ||
                    normalized.contains("tarikh") || normalized.contains("kitne baje")) && !normalized.contains("alarm")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.GET_DEVICE_INFO,
                confidence = 0.92f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "GET_TIME_DATE"
            )
        }

        // 9. Hardware & Settings Control (Flashlight, Volume, Wi-Fi, Bluetooth)
        if (normalized.contains("torch") || normalized.contains("flashlight") || normalized.contains("volume") ||
            normalized.contains("aawaz") || normalized.contains("bluetooth") || normalized.contains("settings")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.CONTROL_DEVICE,
                confidence = 0.94f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "CONTROL_DEVICE"
            )
        }

        // 10. Phone Calling ("call rahul", "dial rahul", "rahul ko call lagao")
        if (normalized.startsWith("call ") || normalized.startsWith("dial ") || normalized.contains(" ko call ") ||
            normalized.endsWith(" ko call lagao") || normalized.endsWith(" ko phone karo")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.MAKE_CALL,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "MAKE_CALL"
            )
        }

        // 11. Messaging (SMS / WhatsApp)
        if (normalized.contains("whatsapp") || normalized.contains("message") || normalized.contains("sms") ||
            normalized.contains("text ") || normalized.contains("ko message bhejo")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SEND_MESSAGE,
                confidence = 0.94f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SEND_MESSAGE"
            )
        }

        // 12. Media Playback ("play music", "gaana bajao", "play some music using brave browser")
        if (normalized.startsWith("play ") || normalized.contains("gaana bajao") || normalized.contains("sangeet chalao") ||
            normalized.contains("play music") || normalized.contains("play songs")
        ) {
            val isAmbiguous = (normalized == "play music" || normalized == "gaana bajao" || normalized == "play song")
            return IntentClassificationResult(
                intent = CognitiveIntent.PLAY_MEDIA,
                confidence = if (isAmbiguous) 0.55f else 0.94f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                isAmbiguous = isAmbiguous,
                clarificationOptions = if (isAmbiguous) listOf("Brave Browser", "YouTube", "Spotify", "YouTube Music") else emptyList(),
                matchedPattern = "PLAY_MEDIA"
            )
        }

        // 13. Web Search ("search for python tutorials", "open brave and search for cybersecurity news")
        if (normalized.contains("search for ") || normalized.contains("search ") || normalized.contains("khojo ") ||
            normalized.contains("search karo") || normalized.contains("google ")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SEARCH_WEB,
                confidence = 0.93f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SEARCH_WEB"
            )
        }

        // 14. Wi-Fi Scanning & Wireless Discovery
        if (normalized.contains("scan wifi") || normalized.contains("scan wi-fi") ||
            normalized.contains("wifi scan karo") || normalized.contains("nearby networks") ||
            normalized.contains("aaspas ke wifi") || normalized.contains("find networks")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.SCAN_WIFI,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "SCAN_WIFI"
            )
        }

        // 15. Network Security Audit & Threat Intelligence
        if (normalized.contains("is my wifi safe") || normalized.contains("analyse network") ||
            normalized.contains("analyze network") || normalized.contains("network audit") ||
            normalized.contains("wifi security") || normalized.contains("check wifi security") ||
            normalized.contains("wifi surakshit hai") || normalized.contains("security audit") ||
            normalized.contains("security report") || normalized.contains("is this network secure")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.ANALYSE_NETWORK,
                confidence = 0.96f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "ANALYSE_NETWORK"
            )
        }

        // 16. App Launch ("open youtube", "brave kholo", "launch settings")
        if (normalized.startsWith("open ") || normalized.startsWith("launch ") || normalized.startsWith("start ") ||
            normalized.endsWith(" kholo") || normalized.endsWith(" khol") || normalized.endsWith(" open karo")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.OPEN_APP,
                confidence = 0.95f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "OPEN_APP"
            )
        }

        // 15. General Conversation ("hello", "who are you", "what can you do", "namaste")
        if (normalized == "hello" || normalized == "hi" || normalized == "namaste" || normalized.contains("who are you") ||
            normalized.contains("what can you do") || normalized.contains("tum kaun ho") || normalized.contains("aap kaun hain")
        ) {
            return IntentClassificationResult(
                intent = CognitiveIntent.GENERAL_CONVERSATION,
                confidence = 0.90f,
                rawUtterance = rawUtterance,
                normalizedUtterance = normalized,
                matchedPattern = "GENERAL_CONVERSATION"
            )
        }

        // Fallback: Low-confidence / Unknown
        return IntentClassificationResult(
            intent = CognitiveIntent.UNKNOWN_INTENT,
            confidence = 0.25f,
            rawUtterance = rawUtterance,
            normalizedUtterance = normalized,
            matchedPattern = "FALLBACK"
        )
    }
}
