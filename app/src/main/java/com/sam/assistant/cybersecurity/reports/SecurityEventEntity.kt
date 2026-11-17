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
    tableName = "security_events",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["severity"]),
        Index(value = ["timestamp"])
    ]
)
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: String, // "WIFI_SECURITY", "DNS_ALERT", "THREAT_MATCH", "VPN_MONITOR"
    val severity: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val title: String,
    val details: String,
    val recommendedAction: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SecurityEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SecurityEventEntity): Long

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 50): List<SecurityEventEntity>

    @Query("DELETE FROM security_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM security_events")
    suspend fun clearAll()
}
