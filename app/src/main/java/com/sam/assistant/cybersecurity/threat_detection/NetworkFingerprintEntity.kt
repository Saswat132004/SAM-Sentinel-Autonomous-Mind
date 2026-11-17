package com.sam.assistant.cybersecurity.threat_detection

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "network_fingerprints",
    indices = [
        Index(value = ["bssid"], unique = true),
        Index(value = ["ssid"])
    ]
)
data class NetworkFingerprintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ssid: String,
    val bssid: String,
    val securityType: String,
    val capabilities: String,
    val frequencyMhz: Int,
    val gatewayIp: String? = null,
    val dnsServers: String = "", // Comma-delimited list
    val isTrusted: Boolean = true,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

@Dao
interface NetworkFingerprintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprint(fingerprint: NetworkFingerprintEntity): Long

    @Update
    suspend fun updateFingerprint(fingerprint: NetworkFingerprintEntity)

    @Delete
    suspend fun deleteFingerprint(fingerprint: NetworkFingerprintEntity)

    @Query("SELECT * FROM network_fingerprints WHERE bssid = :bssid LIMIT 1")
    suspend fun getFingerprintByBssid(bssid: String): NetworkFingerprintEntity?

    @Query("SELECT * FROM network_fingerprints WHERE LOWER(ssid) = LOWER(:ssid)")
    suspend fun getFingerprintsBySsid(ssid: String): List<NetworkFingerprintEntity>

    @Query("SELECT * FROM network_fingerprints ORDER BY lastSeen DESC")
    fun getAllFingerprints(): Flow<List<NetworkFingerprintEntity>>

    @Query("SELECT * FROM network_fingerprints ORDER BY lastSeen DESC")
    suspend fun getAllFingerprintsSync(): List<NetworkFingerprintEntity>

    @Query("DELETE FROM network_fingerprints WHERE bssid = :bssid")
    suspend fun deleteByBssid(bssid: String): Int

    @Query("DELETE FROM network_fingerprints")
    suspend fun clearAll()
}
