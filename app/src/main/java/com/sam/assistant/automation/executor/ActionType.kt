package com.sam.assistant.automation.executor

enum class ActionType(val isSensitive: Boolean) {
    VERIFY_APP_INSTALLED(isSensitive = false),
    LAUNCH_APP(isSensitive = false),
    LIST_APPS(isSensitive = false),
    SEARCH_WEB(isSensitive = false),
    OPEN_URL(isSensitive = false),
    SEARCH_YOUTUBE(isSensitive = false),
    PLAY_MEDIA(isSensitive = false),
    READ_WEB_SUMMARY(isSensitive = false),
    MAKE_CALL(isSensitive = true),
    SEND_SMS(isSensitive = true),
    SEND_WHATSAPP(isSensitive = true),
    PREPARE_WHATSAPP(isSensitive = false),
    TOGGLE_FLASHLIGHT(isSensitive = false),
    READ_BATTERY(isSensitive = false),
    READ_TIME_DATE(isSensitive = false),
    OPEN_SETTINGS(isSensitive = false),
    ADJUST_VOLUME(isSensitive = false),
    SET_ALARM(isSensitive = false),
    CREATE_REMINDER(isSensitive = false),
    SAVE_MEMORY(isSensitive = false),
    DELETE_MEMORY(isSensitive = true),
    QUERY_MEMORY(isSensitive = false),
    SCAN_WIFI(isSensitive = false),
    ANALYSE_NETWORK(isSensitive = false),
    ASK_CLARIFICATION(isSensitive = false),
    CONFIRM_ACTION(isSensitive = false),
    CANCEL_ACTION(isSensitive = false),
    CONVERSATIONAL_RESPONSE(isSensitive = false),
    UNKNOWN(isSensitive = false)
}


