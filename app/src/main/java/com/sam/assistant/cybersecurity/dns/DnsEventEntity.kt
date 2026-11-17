package com.sam.assistant.cybersecurity.dns

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
    tableName = "dns_events",
    indices = [
        Index(value = ["queryDomain"]),
        Index(value = ["isFlagged"])
    ]
)
data class DnsEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val queryDomain: String,
    val queryType: String = "A",
    val responseIp: String? = null,
    val isFlagged: Boolean = false,
    val riskReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DnsEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DnsEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DnsEventEntity>)

    @Query("SELECT * FROM dns_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<DnsEventEntity>>

    @Query("SELECT * FROM dns_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 50): List<DnsEventEntity>

    @Query("SELECT * FROM dns_events WHERE isFlagged = 1 ORDER BY timestamp DESC")
    suspend fun getFlaggedEvents(): List<DnsEventEntity>

    @Query("DELETE FROM dns_events")
    suspend fun clearAll()
}
