package com.sam.assistant.voice.state

/**
 * Advanced Voice Interaction Lifecycle States for SAM Stage 2.
 *
 * Full 11-state machine:
 *   INACTIVE                  -> Voice Engine stopped / Device locked
 *   STANDBY                   -> Background/Foreground listening for "Hey SAM"
 *   WAKE_WORD_DETECTED        -> "Hey SAM" matched; preparing acknowledgement
 *   LISTENING                 -> Microphone capturing user speech command
 *   PROCESSING                -> ASR transcribing & cognitive planning
 *   NEEDS_CLARIFICATION       -> Asking clarifying question (e.g., music source)
 *   WAITING_FOR_CONFIRMATION  -> Asking for sensitive confirmation (e.g., call/SMS)
 *   SPEAKING                  -> TTS output active (supports user interruption)
 *   FOLLOW_UP_LISTENING       -> Waiting for follow-up command in session window
 *   COMPLETED                 -> Task completed successfully
 *   ERROR                     -> Error condition occurred; diagnostics logged
 */
enum class VoiceState(val description: String) {
    INACTIVE("Voice Engine Inactive"),
    STANDBY("Standby: Waiting for 'Hey SAM'"),
    WAKE_WORD_DETECTED("Wake Word Detected"),
    LISTENING("Listening to command..."),
    PROCESSING("Transcribing and processing..."),
    NEEDS_CLARIFICATION("Needs Clarification"),
    WAITING_FOR_CONFIRMATION("Waiting for Confirmation"),
    SPEAKING("Responding..."),
    FOLLOW_UP_LISTENING("Listening for follow-up..."),
    COMPLETED("Command Completed"),
    ERROR("Voice Engine Error")
}
