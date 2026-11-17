package com.sam.assistant.cybersecurity.threat_intel

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
    tableName = "threat_indicators",
    indices = [
        Index(value = ["indicatorValue"], unique = true),
        Index(value = ["indicatorType"]),
        Index(value = ["threatCategory"])
    ]
)
data class ThreatIndicatorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val indicatorType: String, // "IP", "DOMAIN", "PATTERN"
    val indicatorValue: String,
    val threatCategory: String, // "PHISHING", "MALWARE_C2", "SUSPICIOUS_SCANNER", "BOTNET"
    val confidence: Float = 0.9f,
    val sourceFeed: String = "Built-in Offline Intelligence",
    val importTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface ThreatIndicatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndicator(indicator: ThreatIndicatorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndicators(indicators: List<ThreatIndicatorEntity>)

    @Delete
    suspend fun deleteIndicator(indicator: ThreatIndicatorEntity)

    @Query("SELECT * FROM threat_indicators WHERE LOWER(indicatorValue) = LOWER(:value) LIMIT 1")
    suspend fun findExactMatch(value: String): ThreatIndicatorEntity?

    @Query("SELECT * FROM threat_indicators WHERE LOWER(indicatorValue) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchIndicators(query: String): List<ThreatIndicatorEntity>

    @Query("SELECT * FROM threat_indicators ORDER BY importTimestamp DESC")
    fun getAllIndicators(): Flow<List<ThreatIndicatorEntity>>

    @Query("SELECT * FROM threat_indicators ORDER BY importTimestamp DESC")
    suspend fun getAllIndicatorsSync(): List<ThreatIndicatorEntity>

    @Query("DELETE FROM threat_indicators WHERE sourceFeed = :feedName")
    suspend fun deleteByFeed(feedName: String): Int

    @Query("DELETE FROM threat_indicators")
    suspend fun clearAll()
}
