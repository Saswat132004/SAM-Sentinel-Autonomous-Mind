package com.sam.assistant.knowledge.productivity

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val topic: String,
    val durationMinutes: Int = 45,
    val scheduledDate: String, // e.g. "2026-11-15"
    val isCompleted: Boolean = false,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_assignments")
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val title: String,
    val deadlineTimestamp: Long,
    val isSubmitted: Boolean = false,
    val priority: String = "HIGH", // "LOW", "MEDIUM", "HIGH"
    val notes: String = ""
)

@Dao
interface StudyPlannerDao {

    // Study Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySessionEntity>)

    @Update
    suspend fun updateSession(session: StudySessionEntity)

    @Delete
    suspend fun deleteSession(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions ORDER BY id DESC")
    fun getAllSessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE scheduledDate = :date ORDER BY id ASC")
    suspend fun getSessionsForDate(date: String): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE subject = :subject ORDER BY id DESC")
    suspend fun getSessionsBySubject(subject: String): List<StudySessionEntity>

    // Assignments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: AssignmentEntity): Long

    @Update
    suspend fun updateAssignment(assignment: AssignmentEntity)

    @Delete
    suspend fun deleteAssignment(assignment: AssignmentEntity)

    @Query("SELECT * FROM study_assignments ORDER BY deadlineTimestamp ASC")
    fun getAllAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM study_assignments WHERE isSubmitted = 0 ORDER BY deadlineTimestamp ASC")
    suspend fun getPendingAssignments(): List<AssignmentEntity>

    @Query("DELETE FROM study_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM study_assignments")
    suspend fun clearAssignments()
}
