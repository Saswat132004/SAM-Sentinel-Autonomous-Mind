package com.sam.assistant.automation.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sam.assistant.core.security.LockStateManager
import com.sam.assistant.memory.database.SamDatabase
import com.sam.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "sam_reminders_channel"
        const val CHANNEL_NAME = "SAM Reminders"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra("REMINDER_ID", -1L) ?: -1L
        val rawTitle = intent?.getStringExtra("REMINDER_TITLE") ?: "Reminder"

        val isLocked = LockStateManager.checkDeviceLockState(context)
        // Lock-state security: do not show sensitive private information in notification if locked
        val displayTitle = if (isLocked) "SAM Reminder" else rawTitle
        val displayText = if (isLocked) "Unlock device to view reminder details." else "Scheduled alert from SAM."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for SAM reminders and scheduled tasks"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(reminderId.toInt(), notification)

        // Mark as completed in database
        if (reminderId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                SamDatabase.getInstance(context).reminderDao().markCompleted(reminderId)
            }
        }
    }
}

