package com.sam.assistant.cybersecurity.threat_detection

import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo

class SuspiciousNetworkDetector(
    private val fingerprintManager: NetworkFingerprintManager,
    private val riskScorer: NetworkRiskScorer = NetworkRiskScorer()
) {

    /**
     * Performs end-to-end security analysis of a Wi-Fi network against local baselines and heuristics.
     */
    suspend fun analyzeNetwork(network: WifiNetworkInfo): NetworkSecurityAssessment {
        val indicators = fingerprintManager.compareWithBaseline(network).toMutableList()

        // Additional standalone heuristic checks
        if (network.securityType == com.sam.assistant.cybersecurity.wifi.WifiSecurityType.WEP) {
            indicators.add(
                ThreatIndicator(
                    type = IndicatorType.SECURITY_DOWNGRADE,
                    severity = ThreatSeverity.HIGH,
                    title = "Vulnerable Legacy WEP Protocol",
                    indicator = "WEP 802.11 cipher active",
                    reason = "WEP uses flawed RC4 key scheduling that can be broken in minutes.",
                    confidence = 0.99f,
                    recommendedAction = "Upgrade router security standard to WPA2-AES or WPA3.",
                    limitations = "Found only on legacy embedded hardware."
                )
            )
        }

        val (score, severity) = riskScorer.calculateScore(network, indicators)

        return NetworkSecurityAssessment(
            networkSsid = network.ssid,
            networkBssid = network.bssid,
            riskScore = score,
            riskLevel = severity,
            indicators = indicators,
            observations = network.securityObservations
        )
    }
}
