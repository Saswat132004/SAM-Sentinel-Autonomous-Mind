package com.sam.assistant.cybersecurity.reports

import com.sam.assistant.cybersecurity.dns.DnsEventEntity
import com.sam.assistant.cybersecurity.threat_detection.IndicatorType
import com.sam.assistant.cybersecurity.threat_detection.NetworkSecurityAssessment
import com.sam.assistant.cybersecurity.threat_detection.ThreatSeverity
import com.sam.assistant.cybersecurity.traffic.TrafficStatistics
import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Compiles a comprehensive, calibrated local cybersecurity audit report.
 * Strictly adheres to privacy guidelines and objective indicator verification.
 */
class SecurityReportGenerator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun generateReport(
        connectedWifi: WifiNetworkInfo?,
        assessment: NetworkSecurityAssessment?,
        trafficStats: TrafficStatistics? = null,
        recentDnsEvents: List<DnsEventEntity> = emptyList(),
        threatIndicatorCount: Int = 0
    ): SecurityReportEntity {
        val reportId = "SEC-REP-" + UUID.randomUUID().toString().take(8).uppercase()
        val now = System.currentTimeMillis()
        val timestampStr = dateFormat.format(Date(now))
        val ssid = connectedWifi?.ssid ?: assessment?.networkSsid ?: "Disconnected / Unknown"

        val riskScore = assessment?.riskScore ?: 0
        val riskLevel = assessment?.riskLevel?.name ?: ThreatSeverity.LOW.name

        val recommendations = buildRecommendations(connectedWifi, assessment, recentDnsEvents)
        val recommendationsText = recommendations.joinToString("\n") { "• $it" }

        val summaryText = buildSummary(ssid, riskScore, riskLevel, assessment?.indicators?.size ?: 0, recentDnsEvents.count { it.isFlagged })

        val fullContent = buildString {
            appendLine("==================================================================")
            appendLine("SAM CYBERSECURITY AUDIT REPORT — $reportId")
            appendLine("==================================================================")
            appendLine("Generated At: $timestampStr")
            appendLine("Target Device: realme P4 5G (SAM Autonomous Agent)")
            appendLine("Overall Risk Score: $riskScore / 100 [$riskLevel]")
            appendLine()
            appendLine("--- 1. CONNECTED NETWORK INFORMATION ---")
            if (connectedWifi != null) {
                appendLine("SSID: ${connectedWifi.ssid}")
                appendLine("BSSID: ${connectedWifi.bssid}")
                appendLine("Security Type: ${connectedWifi.securityType.displayName}")
                appendLine("Frequency Band: ${connectedWifi.band}")
                appendLine("Frequency: ${connectedWifi.frequencyMhz} MHz")
                appendLine("Channel: ${connectedWifi.channel}")
                appendLine("Signal Strength: ${connectedWifi.rssiDbm} dBm (${connectedWifi.signalLevelPercent}%)")
                appendLine("Link Speed: ${if (connectedWifi.linkSpeedMbps > 0) "${connectedWifi.linkSpeedMbps} Mbps" else "N/A"}")
                appendLine("IP Address: ${connectedWifi.ipAddress ?: "N/A"}")
                appendLine("Gateway: ${connectedWifi.gatewayIp ?: "N/A"}")
                appendLine("DNS Servers: ${if (connectedWifi.dnsServers.isNotEmpty()) connectedWifi.dnsServers.joinToString(", ") else "N/A"}")
                appendLine("Captive Portal Detected: ${if (connectedWifi.hasCaptivePortal) "YES" else "NO"}")
            } else {
                appendLine("No active Wi-Fi connection detected at scan time.")
            }
            appendLine()
            appendLine("--- 2. SECURITY CONFIGURATION ANALYSIS ---")
            if (assessment != null) {
                val hasBaseline = assessment.indicators.none { it.type == IndicatorType.SSID_BSSID_MISMATCH }
                appendLine("Baseline Established: ${if (hasBaseline) "YES" else "NO (New Network or BSSID Changed)"}")
                appendLine("Capabilities String: ${connectedWifi?.capabilities ?: "N/A"}")
                appendLine("Encryption Rating: ${if (connectedWifi?.securityType?.isSecure == true) "Robust" else "Insecure/None"}")
            } else {
                appendLine("Standard configuration assessment pending.")
            }
            appendLine()
            appendLine("--- 3. OBSERVED THREAT INDICATORS ---")
            val indicators = assessment?.indicators ?: emptyList()
            if (indicators.isEmpty()) {
                appendLine("No suspicious indicators or anomalies detected for this network.")
            } else {
                indicators.forEachIndexed { idx, ind ->
                    appendLine("${idx + 1}. [${ind.severity.name}] ${ind.title} (Confidence: ${(ind.confidence * 100).toInt()}%)")
                    appendLine("   Details: ${ind.reason}")
                    appendLine("   Evidence: ${ind.indicator}")
                }
            }
            appendLine()
            appendLine("--- 4. NETWORK TRAFFIC MONITORING SUMMARY ---")
            if (trafficStats != null && trafficStats.totalPackets > 0) {
                val durSec = if (trafficStats.sessionStartTimeMs > 0) (now - trafficStats.sessionStartTimeMs) / 1000 else 0
                appendLine("Session Duration: ${durSec}s")
                appendLine("Packets Processed: ${trafficStats.totalPackets}")
                appendLine("Total Bytes: ${trafficStats.bytesRx + trafficStats.bytesTx} B (Rx: ${trafficStats.bytesRx} B, Tx: ${trafficStats.bytesTx} B)")
                appendLine("Protocol Breakdown: ${trafficStats.protocolBreakdown}")
                appendLine("Unique Destination Remote IPs: ${trafficStats.topDestinations.keys.size}")
            } else {
                appendLine("Traffic monitoring service not active or session stats unavailable.")
            }
            appendLine()
            appendLine("--- 5. DNS SECURITY OBSERVATIONS ---")
            appendLine("Total DNS Queries Logged: ${recentDnsEvents.size}")
            val suspiciousDns = recentDnsEvents.filter { it.isFlagged }
            appendLine("Suspicious DNS Queries: ${suspiciousDns.size}")
            if (suspiciousDns.isNotEmpty()) {
                suspiciousDns.take(5).forEach { dns ->
                    appendLine("  • ${dns.queryDomain} (Reason: ${dns.riskReason ?: "Suspicious pattern"})")
                }
            }
            appendLine()
            appendLine("--- 6. LOCAL THREAT INTELLIGENCE MATCHES ---")
            appendLine("Loaded Threat Indicators in Database: $threatIndicatorCount")
            appendLine()
            appendLine("--- 7. RECOMMENDATIONS & MITIGATIONS ---")
            if (recommendations.isEmpty()) {
                appendLine("• All network security controls appear normal. Continue standard security hygiene.")
            } else {
                recommendations.forEach { appendLine("• $it") }
            }
            appendLine()
            appendLine("--- 8. METHODOLOGY & DISCLAIMER ---")
            appendLine("Notice: This audit is conducted 100% offline and locally on this device.")
            appendLine("SAM monitors transport headers, Wi-Fi beacons, and DNS query names without intercepting or decrypting payload contents or breaking TLS certificates.")
            appendLine("Scores and classifications are advisory indicators and do not constitute a legal or commercial guarantee.")
            appendLine("==================================================================")
        }

        return SecurityReportEntity(
            reportId = reportId,
            title = "Security Audit: $ssid",
            generatedTimestamp = now,
            overallRiskScore = riskScore,
            riskLevel = riskLevel,
            connectedNetworkSsid = ssid,
            summaryText = summaryText,
            recommendationsText = recommendationsText,
            fullReportContent = fullContent
        )
    }

    private fun buildSummary(ssid: String, score: Int, level: String, indCount: Int, dnsAlerts: Int): String {
        return "Network '$ssid' scored $score/100 risk level ($level). Found $indCount threat indicators and $dnsAlerts suspicious DNS events."
    }

    private fun buildRecommendations(
        wifi: WifiNetworkInfo?,
        assessment: NetworkSecurityAssessment?,
        dnsEvents: List<DnsEventEntity>
    ): List<String> {
        val list = mutableListOf<String>()

        if (wifi?.securityType?.isSecure == false) {
            list.add("IMMEDIATE: Disconnect or avoid transmitting unencrypted personal information over this open/unencrypted Wi-Fi network.")
        }
        if (wifi?.securityType?.name == "WEP") {
            list.add("CRITICAL: WEP encryption is deprecated and easily compromised. Upgrade router configuration to WPA2-PSK (AES) or WPA3-SAE.")
        }
        if (assessment != null && assessment.riskScore >= 70) {
            list.add("HIGH PRIORITY: Network exhibits high anomaly score. Disconnect and verify router hardware or contact network administrator.")
        }
        if (dnsEvents.any { it.isFlagged }) {
            list.add("Review flagged DNS queries in the DNS Security log. Ensure known legitimate apps originate requested domains.")
        }
        if (wifi != null && wifi.band == "2.4 GHz" && wifi.channel == 6) {
            list.add("Channel 6 on 2.4 GHz is frequently congested. Consider switching router to 5 GHz or less congested channels (1, 11).")
        }
        if (list.isEmpty()) {
            list.add("Maintain regular network audits and verify unknown access points before connecting.")
        }
        return list
    }
}
