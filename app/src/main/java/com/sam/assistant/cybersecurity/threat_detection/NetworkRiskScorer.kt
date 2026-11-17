package com.sam.assistant.cybersecurity.threat_detection

import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo
import com.sam.assistant.cybersecurity.wifi.WifiSecurityType

class NetworkRiskScorer {

    /**
     * Calculates transparent risk score (0 to 100) and risk severity level.
     */
    fun calculateScore(
        network: WifiNetworkInfo,
        indicators: List<ThreatIndicator>
    ): Pair<Int, ThreatSeverity> {
        var score = 0

        // 1. Base score from Wi-Fi security protocol
        when (network.securityType) {
            WifiSecurityType.OPEN -> score += 35
            WifiSecurityType.WEP -> score += 40
            WifiSecurityType.WPA -> score += 20
            WifiSecurityType.WPA2_PSK -> score += 5
            WifiSecurityType.WPA3_SAE -> score += 0
            WifiSecurityType.EAP_ENTERPRISE -> score += 0
            WifiSecurityType.UNKNOWN -> score += 25
        }

        // 2. WPS vulnerability risk
        if (network.capabilities.contains("WPS")) {
            score += 10
        }

        // 3. Add penalty for detected indicators based on severity
        for (indicator in indicators) {
            score += when (indicator.severity) {
                ThreatSeverity.CRITICAL -> 40
                ThreatSeverity.HIGH -> 30
                ThreatSeverity.MEDIUM -> 15
                ThreatSeverity.LOW -> 5
            }
        }

        val clampedScore = score.coerceIn(0, 100)
        val level = when {
            clampedScore >= 80 -> ThreatSeverity.CRITICAL
            clampedScore >= 60 -> ThreatSeverity.HIGH
            clampedScore >= 30 -> ThreatSeverity.MEDIUM
            else -> ThreatSeverity.LOW
        }

        return Pair(clampedScore, level)
    }
}
