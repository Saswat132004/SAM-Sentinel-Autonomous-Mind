package com.sam.assistant.cybersecurity.traffic

data class TrafficStatistics(
    val isMonitoringActive: Boolean = false,
    val sessionStartTimeMs: Long = 0,
    val bytesRx: Long = 0,
    val bytesTx: Long = 0,
    val totalPackets: Long = 0,
    val protocolBreakdown: Map<String, Long> = emptyMap(),
    val topDestinations: Map<String, Long> = emptyMap()
) {
    val totalKiloBytes: Float
        get() = (bytesRx + bytesTx) / 1024f

    val totalMegaBytes: Float
        get() = totalKiloBytes / 1024f
}
