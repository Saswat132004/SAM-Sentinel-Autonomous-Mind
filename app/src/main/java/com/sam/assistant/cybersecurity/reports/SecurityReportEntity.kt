package com.sam.assistant.cybersecurity.reports

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "security_reports",
    indices = [
        Index(value = ["reportId"], unique = true),
        Index(value = ["generatedTimestamp"])
    ]
)
data class SecurityReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reportId: String,
    val title: String,
    val generatedTimestamp: Long = System.currentTimeMillis(),
    val overallRiskScore: Int,
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val connectedNetworkSsid: String,
    val summaryText: String,
    val recommendationsText: String,
    val fullReportContent: String
)

@Dao
interface SecurityReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: SecurityReportEntity): Long

    @Query("SELECT * FROM security_reports ORDER BY generatedTimestamp DESC")
    fun getAllReports(): Flow<List<SecurityReportEntity>>

    @Query("SELECT * FROM security_reports WHERE reportId = :reportId LIMIT 1")
    suspend fun getReportById(reportId: String): SecurityReportEntity?

    @Query("DELETE FROM security_reports WHERE reportId = :reportId")
    suspend fun deleteById(reportId: String): Int

    @Query("DELETE FROM security_reports")
    suspend fun clearAll()
}
