package com.sam.assistant.cybersecurity.threat_detection

enum class ThreatSeverity(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical")
}

enum class IndicatorType {
    SSID_BSSID_MISMATCH,
    SECURITY_DOWNGRADE,
    ROGUE_AP_INDICATOR,
    UNUSUAL_DNS,
    OPEN_NETWORK_WARNING,
    SUSPICIOUS_DOMAIN,
    KNOWN_THREAT
}

data class ThreatIndicator(
    val type: IndicatorType,
    val severity: ThreatSeverity,
    val title: String,
    val indicator: String,
    val reason: String,
    val confidence: Float, // 0.0f to 1.0f
    val recommendedAction: String,
    val limitations: String
)

data class NetworkSecurityAssessment(
    val networkSsid: String,
    val networkBssid: String,
    val riskScore: Int, // 0 to 100
    val riskLevel: ThreatSeverity,
    val indicators: List<ThreatIndicator>,
    val observations: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
