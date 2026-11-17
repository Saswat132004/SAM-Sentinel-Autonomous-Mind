package com.sam.assistant.automation.device

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class SamAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SamAccessibility"
        var instance: SamAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "SAM Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only monitor events if explicitly requested by active automation
    }

    override fun onInterrupt() {
        Log.d(TAG, "SAM Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun performBackGesture(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHomeGesture(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecentsGesture(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}

