package com.sam.assistant.automation.device

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sam.assistant.core.security.LockStateManager

data class CapturedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

class SamNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SamNotifListener"
        var isEnabledByUser = false
        private val recentNotifications = mutableListOf<CapturedNotification>()

        fun getRecentNotifications(): List<CapturedNotification> {
            if (LockStateManager.isLocked.value) return emptyList()
            synchronized(recentNotifications) {
                return recentNotifications.toList()
            }
        }

        fun clearNotifications() {
            synchronized(recentNotifications) {
                recentNotifications.clear()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!isEnabledByUser || sbn == null) return

        // Strict lock-state security: Never ingest private notification contents while device is locked
        if (LockStateManager.isLocked.value) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isNotEmpty() || text.isNotEmpty()) {
            synchronized(recentNotifications) {
                recentNotifications.add(
                    0,
                    CapturedNotification(
                        packageName = sbn.packageName,
                        title = title,
                        text = text,
                        timestamp = sbn.postTime
                    )
                )
                // Keep only last 20 notifications locally in memory
                if (recentNotifications.size > 20) {
                    recentNotifications.removeAt(recentNotifications.lastIndex)
                }
            }
            Log.d(TAG, "Notification received locally from ${sbn.packageName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification dismissed
    }
}

