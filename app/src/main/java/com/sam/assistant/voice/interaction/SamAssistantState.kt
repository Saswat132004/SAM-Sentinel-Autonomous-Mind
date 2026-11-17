package com.sam.assistant.voice.interaction

enum class SamAssistantState(val description: String) {
    STANDBY("Standby (Waiting for 'Hey SAM')"),
    WAKE_WORD_DETECTED("Wake Word Detected"),
    LISTENING("Listening..."),
    PROCESSING("Interpreting Command..."),
    EXECUTING("Executing Action..."),
    SPEAKING("Speaking..."),
    LOCKED("Device Locked (Security Guard Active)"),
    ERROR("Error")
}

