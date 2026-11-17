package com.sam.assistant.automation.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.sam.assistant.automation.executor.ExecutionResult
import com.sam.assistant.core.permissions.PermissionManager
import com.sam.assistant.memory.repository.MemoryRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

open class ReminderScheduler(
    private val context: Context,
    private val memoryRepository: MemoryRepository
) {

    open suspend fun createReminder(title: String, delayMinutes: Int): ExecutionResult {
        val triggerTimeMs = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val id = memoryRepository.saveReminder(title, triggerTimeMs)

        if (PermissionManager.canScheduleExactAlarms(context)) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", id)
                putExtra("REMINDER_TITLE", title)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, id.toInt(), intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
            val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerTimeMs))
            return ExecutionResult.Success("Reminder set for $formattedTime: \"$title\".")
        } else {
            return ExecutionResult.Success("Reminder saved locally: \"$title\". Note: Exact alarm permission is required for alarm notification.")
        }
    }

    fun setSystemAlarm(hour: Int, minute: Int, message: String = "SAM Alarm"): ExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            ExecutionResult.Success("Alarm set for $formattedTime.")
        } catch (e: Exception) {
            ExecutionResult.Error("Could not set alarm: ${e.message}")
        }
    }
}

