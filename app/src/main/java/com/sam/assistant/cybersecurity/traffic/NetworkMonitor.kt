package com.sam.assistant.cybersecurity.traffic

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class VpnMonitorStatus {
    object Inactive : VpnMonitorStatus()
    object Active : VpnMonitorStatus()
    data class ConsentRequired(val consentIntent: Intent) : VpnMonitorStatus()
    data class Error(val message: String) : VpnMonitorStatus()
}

class NetworkMonitor(
    private val context: Context,
    private val statisticsDao: TrafficStatisticsDao? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var statsJob: Job? = null
    private var sessionStartTime: Long = 0
    private val gson = Gson()

    private val _status = MutableStateFlow<VpnMonitorStatus>(VpnMonitorStatus.Inactive)
    val status: StateFlow<VpnMonitorStatus> = _status.asStateFlow()

    private val _liveStats = MutableStateFlow(TrafficStatistics())
    val liveStats: StateFlow<TrafficStatistics> = _liveStats.asStateFlow()

    fun getConsentIntent(): Intent? = VpnService.prepare(context)

    /**
     * Checks whether Android VPN consent is needed or starts monitoring if granted.
     */
    fun startMonitoring(): VpnMonitorStatus {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            val status = VpnMonitorStatus.ConsentRequired(prepareIntent)
            _status.value = status
            return status
        }

        val startIntent = Intent(context, SamVpnService::class.java).apply {
            action = SamVpnService.ACTION_START
        }
        context.startService(startIntent)
        sessionStartTime = System.currentTimeMillis()
        _status.value = VpnMonitorStatus.Active

        startStatsPolling()
        return VpnMonitorStatus.Active
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && SamVpnService.isRunning) {
                _liveStats.value = TrafficStatistics(
                    isMonitoringActive = true,
                    sessionStartTimeMs = sessionStartTime,
                    bytesRx = SamVpnService.liveBytesRx.get(),
                    bytesTx = SamVpnService.liveBytesTx.get(),
                    totalPackets = SamVpnService.livePacketCount.get(),
                    protocolBreakdown = SamVpnService.liveProtocols.toMap(),
                    topDestinations = SamVpnService.liveDestinations.toMap()
                )
                delay(1000)
            }
        }
    }

    /**
     * Stops the active VPN monitoring session and saves session summary to database.
     */
    suspend fun stopMonitoring(): TrafficStatistics {
        val stopIntent = Intent(context, SamVpnService::class.java).apply {
            action = SamVpnService.ACTION_STOP
        }
        context.startService(stopIntent)
        statsJob?.cancel()

        val finalStats = _liveStats.value.copy(isMonitoringActive = false)
        _liveStats.value = finalStats
        _status.value = VpnMonitorStatus.Inactive

        // Save session summary
        if (sessionStartTime > 0) {
            val sessionEntity = TrafficStatisticsEntity(
                sessionStartTime = sessionStartTime,
                sessionEndTime = System.currentTimeMillis(),
                totalBytesRx = finalStats.bytesRx,
                totalBytesTx = finalStats.bytesTx,
                packetCount = finalStats.totalPackets,
                protocolSummaryJson = gson.toJson(finalStats.protocolBreakdown),
                topDestinationsJson = gson.toJson(finalStats.topDestinations)
            )
            statisticsDao?.insertSession(sessionEntity)
            sessionStartTime = 0
        }

        return finalStats
    }

    fun isRunning(): Boolean = SamVpnService.isRunning
}
