package com.sam.assistant.automation.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.memory.database.SamDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!PermissionManager.canScheduleExactAlarms(context)) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = SamDatabase.getInstance(context)
            val activeReminders = db.reminderDao().getActiveReminders().first()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val now = System.currentTimeMillis()
            for (reminder in activeReminders) {
                if (reminder.triggerTimeMs > now) {
                    val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("REMINDER_ID", reminder.id)
                        putExtra("REMINDER_TITLE", reminder.title)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val pendingIntent = PendingIntent.getBroadcast(context, reminder.id.toInt(), reminderIntent, flags)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerTimeMs, pendingIntent)
                    }
                }
            }
        }
    }
}

