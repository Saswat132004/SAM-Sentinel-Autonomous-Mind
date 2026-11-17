package com.sam.assistant.knowledge.productivity

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ProposedStudyPlan(
    val totalSessions: Int,
    val totalHours: Float,
    val sessions: List<StudySessionEntity>,
    val explanation: String
)

class StudyPlanner(private val dao: StudyPlannerDao) {

    suspend fun addSession(
        subject: String,
        topic: String,
        durationMinutes: Int = 45,
        scheduledDate: String
    ): Long {
        val session = StudySessionEntity(
            subject = subject,
            topic = topic,
            durationMinutes = durationMinutes,
            scheduledDate = scheduledDate
        )
        return dao.insertSession(session)
    }

    suspend fun completeSession(sessionId: Long) {
        val all = dao.getSessionsForDate("")
        val existing = all.find { it.id == sessionId }
        if (existing != null) {
            dao.updateSession(existing.copy(isCompleted = true))
        }
    }

    suspend fun addAssignment(
        subject: String,
        title: String,
        deadlineTimestamp: Long,
        priority: String = "HIGH"
    ): Long {
        val assignment = AssignmentEntity(
            subject = subject,
            title = title,
            deadlineTimestamp = deadlineTimestamp,
            priority = priority
        )
        return dao.insertAssignment(assignment)
    }

    suspend fun getPendingAssignments(): List<AssignmentEntity> {
        return dao.getPendingAssignments()
    }

    /**
     * Generates a proposed revision schedule distributed evenly across available study hours.
     */
    fun generateProposedWeeklySchedule(
        subjects: List<String>,
        availableHoursPerDay: Int = 2
    ): ProposedStudyPlan {
        if (subjects.isEmpty()) {
            return ProposedStudyPlan(0, 0f, emptyList(), "No subjects specified for study planning.")
        }

        val proposedSessions = mutableListOf<StudySessionEntity>()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val sessionsPerDay = (availableHoursPerDay * 60) / 45 // 45-min blocks
        var subjectIndex = 0

        for (day in 0..6) {
            val dateStr = dateFormat.format(calendar.time)
            for (s in 1..sessionsPerDay) {
                val subject = subjects[subjectIndex % subjects.size]
                proposedSessions.add(
                    StudySessionEntity(
                        subject = subject,
                        topic = "Revision Block $s",
                        durationMinutes = 45,
                        scheduledDate = dateStr
                    )
                )
                subjectIndex++
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val totalHours = (proposedSessions.size * 45f) / 60f

        return ProposedStudyPlan(
            totalSessions = proposedSessions.size,
            totalHours = totalHours,
            sessions = proposedSessions,
            explanation = "Proposed 7-day revision schedule with ${proposedSessions.size} sessions ($totalHours total hours) across ${subjects.size} subjects."
        )
    }
}
