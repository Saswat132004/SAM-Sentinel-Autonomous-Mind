package com.sam.assistant.knowledge.productivity

import com.sam.assistant.automation.reminders.ReminderScheduler
import com.sam.assistant.memory.model.ReminderEntity
import com.sam.assistant.memory.repository.MemoryRepository
import kotlinx.coroutines.flow.first

sealed class ScheduleReminderResult {
    data class Scheduled(val reminderId: Long, val message: String) : ScheduleReminderResult()
    data class DuplicatePrevented(val existingReminder: ReminderEntity, val reason: String) : ScheduleReminderResult()
    data class Failed(val error: String) : ScheduleReminderResult()
}

data class DailyProductivityOverview(
    val date: String,
    val pendingStudySessions: Int,
    val upcomingAssignments: Int,
    val activeReminders: Int,
    val summaryText: String
)

class ProductivityManager(
    private val memoryRepository: MemoryRepository,
    private val reminderScheduler: ReminderScheduler,
    private val studyPlanner: StudyPlanner
) {

    /**
     * Schedules a reminder safely with duplicate prevention.
     */
    suspend fun scheduleStudyReminder(
        message: String,
        targetTimeMs: Long,
        subject: String = "Study"
    ): ScheduleReminderResult {
        // 1. Duplicate check against existing active reminders
        val existingReminders = memoryRepository.getActiveReminders().first()
        val timeThresholdMs = 300000L // 5 minutes window

        val duplicate = existingReminders.find { rem ->
            val sameMessage = rem.title.contains(message, ignoreCase = true) || message.contains(rem.title, ignoreCase = true)
            val closeTime = kotlin.math.abs(rem.triggerTimeMs - targetTimeMs) < timeThresholdMs
            sameMessage && closeTime
        }

        if (duplicate != null) {
            return ScheduleReminderResult.DuplicatePrevented(
                existingReminder = duplicate,
                reason = "A reminder for \"$message\" is already scheduled at ${java.util.Date(duplicate.triggerTimeMs)}. Duplicate creation prevented."
            )
        }

        // 2. Schedule via ReminderScheduler / memoryRepository
        val delayMinutes = ((targetTimeMs - System.currentTimeMillis()) / 60000L).coerceAtLeast(1L).toInt()
        reminderScheduler.createReminder("[$subject] $message", delayMinutes)
        val id = memoryRepository.saveReminder("[$subject] $message", targetTimeMs)

        return ScheduleReminderResult.Scheduled(id, "Scheduled study reminder for \"$message\".")
    }

    /**
     * Generates a comprehensive daily productivity and study overview.
     */
    suspend fun getDailyOverview(dateStr: String): DailyProductivityOverview {
        val pendingAssignments = studyPlanner.getPendingAssignments()
        val activeReminders = memoryRepository.getActiveReminders().first().filter { it.triggerTimeMs >= System.currentTimeMillis() }

        val summary = "Daily Overview: ${pendingAssignments.size} pending assignments, " +
                "${activeReminders.size} active reminders scheduled."

        return DailyProductivityOverview(
            date = dateStr,
            pendingStudySessions = 0,
            upcomingAssignments = pendingAssignments.size,
            activeReminders = activeReminders.size,
            summaryText = summary
        )
    }
}
