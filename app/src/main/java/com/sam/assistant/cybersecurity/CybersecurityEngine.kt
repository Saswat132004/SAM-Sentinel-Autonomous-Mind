package com.sam.assistant.cybersecurity

import android.content.Context
import com.sam.assistant.cybersecurity.dns.DnsEventDao
import com.sam.assistant.cybersecurity.dns.DnsEventEntity
import com.sam.assistant.cybersecurity.dns.DnsMonitor
import com.sam.assistant.cybersecurity.reports.SecurityEventDao
import com.sam.assistant.cybersecurity.reports.SecurityEventEntity
import com.sam.assistant.cybersecurity.reports.SecurityReportDao
import com.sam.assistant.cybersecurity.reports.SecurityReportEntity
import com.sam.assistant.cybersecurity.reports.SecurityReportGenerator
import com.sam.assistant.cybersecurity.threat_detection.NetworkFingerprintDao
import com.sam.assistant.cybersecurity.threat_detection.NetworkFingerprintManager
import com.sam.assistant.cybersecurity.threat_detection.NetworkRiskScorer
import com.sam.assistant.cybersecurity.threat_detection.NetworkSecurityAssessment
import com.sam.assistant.cybersecurity.threat_detection.SuspiciousNetworkDetector
import com.sam.assistant.cybersecurity.threat_detection.ThreatSeverity
import com.sam.assistant.cybersecurity.threat_intel.ThreatAnalysisVerdict
import com.sam.assistant.cybersecurity.threat_intel.ThreatImportResult
import com.sam.assistant.cybersecurity.threat_intel.ThreatIndicatorDao
import com.sam.assistant.cybersecurity.threat_intel.ThreatIntelligenceEngine
import com.sam.assistant.cybersecurity.traffic.NetworkMonitor
import com.sam.assistant.cybersecurity.traffic.TrafficStatisticsDao
import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo
import com.sam.assistant.cybersecurity.wifi.WifiScanManager
import com.sam.assistant.cybersecurity.wifi.WifiScanner
import com.sam.assistant.memory.database.SamDatabase

/**
 * Master facade orchestrating all Stage 7 Cybersecurity Intelligence subsystems.
 * Supports complete offline evaluation, threat correlation, and verified reporting.
 */
open class CybersecurityEngine(
    val context: Context,
    val database: SamDatabase? = null,
    val wifiScanManager: WifiScanManager = WifiScanManager(context),
    networkFingerprintDaoOverride: NetworkFingerprintDao? = null,
    trafficStatisticsDaoOverride: TrafficStatisticsDao? = null,
    dnsEventDaoOverride: DnsEventDao? = null,
    threatIndicatorDaoOverride: ThreatIndicatorDao? = null,
    securityReportDaoOverride: SecurityReportDao? = null,
    securityEventDaoOverride: SecurityEventDao? = null
) {

    val networkFingerprintDao: NetworkFingerprintDao? by lazy {
        networkFingerprintDaoOverride ?: database?.networkFingerprintDao()
    }
    val trafficStatisticsDao: TrafficStatisticsDao? by lazy {
        trafficStatisticsDaoOverride ?: database?.trafficStatisticsDao()
    }
    val dnsEventDao: DnsEventDao? by lazy {
        dnsEventDaoOverride ?: database?.dnsEventDao()
    }
    val threatIndicatorDao: ThreatIndicatorDao? by lazy {
        threatIndicatorDaoOverride ?: database?.threatIndicatorDao()
    }
    val securityReportDao: SecurityReportDao? by lazy {
        securityReportDaoOverride ?: database?.securityReportDao()
    }
    val securityEventDao: SecurityEventDao? by lazy {
        securityEventDaoOverride ?: database?.securityEventDao()
    }

    val networkRiskScorer = NetworkRiskScorer()

    val networkFingerprintManager: NetworkFingerprintManager by lazy {
        NetworkFingerprintManager(networkFingerprintDao)
    }

    val suspiciousNetworkDetector: SuspiciousNetworkDetector by lazy {
        SuspiciousNetworkDetector(networkFingerprintManager, networkRiskScorer)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(context, trafficStatisticsDao)
    }

    val dnsMonitor: DnsMonitor by lazy {
        DnsMonitor(dnsEventDao)
    }

    val threatIntelligenceEngine: ThreatIntelligenceEngine by lazy {
        ThreatIntelligenceEngine(threatIndicatorDao)
    }

    val reportGenerator = SecurityReportGenerator()

    /**
     * Executes a full on-device cybersecurity audit of the currently connected network.
     */
    open suspend fun auditCurrentNetwork(): SecurityReportEntity {
        val currentWifi = wifiScanManager.getCurrentConnection()
        val assessment = if (currentWifi != null) {
            suspiciousNetworkDetector.analyzeNetwork(currentWifi)
        } else {
            null
        }

        val recentDns = dnsEventDao?.getRecentEvents(20) ?: emptyList()
        val trafficStats = networkMonitor.liveStats.value
        val threatCount = threatIndicatorDao?.getAllIndicatorsSync()?.size ?: 0

        val report = reportGenerator.generateReport(
            connectedWifi = currentWifi,
            assessment = assessment,
            trafficStats = trafficStats,
            recentDnsEvents = recentDns,
            threatIndicatorCount = threatCount
        )

        // Save report to database
        securityReportDao?.insertReport(report)

        // If risk is Medium or higher, log a security event
        if (assessment != null && assessment.riskLevel != ThreatSeverity.LOW) {
            logSecurityEvent(
                eventType = "WIFI_SECURITY",
                severity = assessment.riskLevel.name,
                title = "Elevated Network Risk on ${currentWifi?.ssid ?: "Current Network"}",
                details = "Network risk scored ${assessment.riskScore}/100. Observed ${assessment.indicators.size} indicators.",
                recommendedAction = assessment.indicators.firstOrNull()?.recommendedAction ?: "Review network properties"
            )
        }

        return report
    }

    /**
     * Scans for nearby Wi-Fi networks adhering to Android throttling limits.
     */
    open suspend fun scanNearbyWifi(): List<WifiNetworkInfo> {
        return wifiScanManager.startScan()
    }

    /**
     * Evaluates a specific network info.
     */
    open suspend fun assessNetwork(network: WifiNetworkInfo): NetworkSecurityAssessment {
        return suspiciousNetworkDetector.analyzeNetwork(network)
    }

    /**
     * Saves a verified network fingerprint baseline.
     */
    open suspend fun saveNetworkBaseline(network: WifiNetworkInfo) {
        networkFingerprintManager.saveBaseline(network)
    }

    /**
     * Inspects a domain name offline using Shannon entropy, punycode, abuse TLD heuristics,
     * and local threat intelligence feeds.
     */
    open suspend fun inspectDomain(domain: String): DnsEventEntity {
        val dnsEvent = dnsMonitor.recordDnsQuery(domain)
        // Also cross-reference against local threat intelligence
        val verdict = threatIntelligenceEngine.evaluateIndicator(domain)
        if (verdict is ThreatAnalysisVerdict.KnownThreatMatch) {
            logSecurityEvent(
                eventType = "THREAT_MATCH",
                severity = ThreatSeverity.HIGH.name,
                title = "Known Malicious Domain Queried",
                details = "Domain: $domain matched local threat feed category: ${verdict.indicator.threatCategory}.",
                recommendedAction = verdict.userAdvice
            )
        }
        return dnsEvent
    }

    /**
     * Imports threat intelligence indicators from local CSV or JSON strings.
     */
    open suspend fun importThreatIntel(feedContent: String, format: String, sourceName: String): ThreatImportResult {
        return threatIntelligenceEngine.importer.importThreatFeed(feedContent, format, sourceName)
    }

    /**
     * Records a security event.
     */
    open suspend fun logSecurityEvent(
        eventType: String,
        severity: String,
        title: String,
        details: String,
        recommendedAction: String = ""
    ) {
        val event = SecurityEventEntity(
            eventType = eventType,
            severity = severity,
            title = title,
            details = details,
            recommendedAction = recommendedAction,
            timestamp = System.currentTimeMillis()
        )
        securityEventDao?.insertEvent(event)
    }

    /**
     * Initializes default built-in threat rules if database is empty.
     */
    open suspend fun initializeDefaults() {
        threatIntelligenceEngine.initializeDefaultRulesIfNeeded()
    }

    /**
     * Quick status summary for voice or agent replies.
     */
    open suspend fun getQuickSecuritySummary(): String {
        val current = wifiScanManager.getCurrentConnection()
        return if (current == null) {
            "Wi-Fi is currently disconnected. No network risk detected."
        } else {
            val assessment = suspiciousNetworkDetector.analyzeNetwork(current)
            "Connected to ${current.ssid} using ${current.securityType.displayName} encryption on ${current.band}. Overall risk score is ${assessment.riskScore} out of 100, classified as ${assessment.riskLevel.displayName}."
        }
    }
}
