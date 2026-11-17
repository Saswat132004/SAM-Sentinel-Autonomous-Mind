package com.sam.assistant.cybersecurity.traffic

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "traffic_sessions")
data class TrafficStatisticsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionStartTime: Long,
    val sessionEndTime: Long,
    val totalBytesRx: Long,
    val totalBytesTx: Long,
    val packetCount: Long,
    val protocolSummaryJson: String = "{}",
    val topDestinationsJson: String = "{}"
)

@Dao
interface TrafficStatisticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrafficStatisticsEntity): Long

    @Query("SELECT * FROM traffic_sessions ORDER BY sessionEndTime DESC")
    fun getAllSessions(): Flow<List<TrafficStatisticsEntity>>

    @Query("SELECT * FROM traffic_sessions ORDER BY sessionEndTime DESC LIMIT 1")
    suspend fun getLatestSession(): TrafficStatisticsEntity?

    @Query("DELETE FROM traffic_sessions")
    suspend fun clearAll()
}
