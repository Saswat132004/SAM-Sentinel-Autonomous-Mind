package com.sam.assistant.cybersecurity

import com.sam.assistant.ai.cognitive.intent.IntentClassifier
import com.sam.assistant.ai.cognitive.models.CognitiveIntent
import com.sam.assistant.cybersecurity.dns.*
import com.sam.assistant.cybersecurity.reports.*
import com.sam.assistant.cybersecurity.threat_detection.*
import com.sam.assistant.cybersecurity.threat_intel.*
import com.sam.assistant.cybersecurity.traffic.*
import com.sam.assistant.cybersecurity.wifi.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class Stage7CybersecurityEngineTest {

    // In-memory DAOs
    private lateinit var fakeFingerprintDao: FakeNetworkFingerprintDao
    private lateinit var fakeTrafficDao: FakeTrafficStatisticsDao
    private lateinit var fakeDnsDao: FakeDnsEventDao
    private lateinit var fakeThreatDao: FakeThreatIndicatorDao
    private lateinit var fakeReportDao: FakeSecurityReportDao
    private lateinit var fakeEventDao: FakeSecurityEventDao

    // Engines
    private lateinit var fingerprintManager: NetworkFingerprintManager
    private lateinit var riskScorer: NetworkRiskScorer
    private lateinit var suspiciousDetector: SuspiciousNetworkDetector
    private lateinit var dnsQueryAnalyzer: DnsQueryAnalyzer
    private lateinit var dnsMonitor: DnsMonitor
    private lateinit var threatIntelligenceEngine: ThreatIntelligenceEngine
    private lateinit var reportGenerator: SecurityReportGenerator

    @Before
    fun setUp() {
        fakeFingerprintDao = FakeNetworkFingerprintDao()
        fakeTrafficDao = FakeTrafficStatisticsDao()
        fakeDnsDao = FakeDnsEventDao()
        fakeThreatDao = FakeThreatIndicatorDao()
        fakeReportDao = FakeSecurityReportDao()
        fakeEventDao = FakeSecurityEventDao()

        fingerprintManager = NetworkFingerprintManager(fakeFingerprintDao)
        riskScorer = NetworkRiskScorer()
        suspiciousDetector = SuspiciousNetworkDetector(fingerprintManager, riskScorer)
        dnsQueryAnalyzer = DnsQueryAnalyzer()
        dnsMonitor = DnsMonitor(fakeDnsDao, dnsQueryAnalyzer)
        threatIntelligenceEngine = ThreatIntelligenceEngine(fakeThreatDao)
        reportGenerator = SecurityReportGenerator()
    }

    // -------------------------------------------------------------
    // 1. Wi-Fi Security Classification Tests
    // -------------------------------------------------------------

    @Test
    fun testWifiSecurityClassificationOpen() {
        val type = WifiSecurityClassifier.classifySecurity("[ESS]")
        assertEquals(WifiSecurityType.OPEN, type)
        assertFalse(type.isSecure)
    }

    @Test
    fun testWifiSecurityClassificationWep() {
        val type = WifiSecurityClassifier.classifySecurity("[WEP][ESS]")
        assertEquals(WifiSecurityType.WEP, type)
        assertFalse(type.isSecure)
    }

    @Test
    fun testWifiSecurityClassificationWpa2() {
        val type = WifiSecurityClassifier.classifySecurity("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]")
        assertEquals(WifiSecurityType.WPA2_PSK, type)
        assertTrue(type.isSecure)
    }

    @Test
    fun testWifiSecurityClassificationWpa3Sae() {
        val type = WifiSecurityClassifier.classifySecurity("[RSN-SAE-CCMP][ESS]")
        assertEquals(WifiSecurityType.WPA3_SAE, type)
        assertTrue(type.isSecure)
    }

    @Test
    fun testWifiSecurityClassificationEnterprise() {
        val type = WifiSecurityClassifier.classifySecurity("[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]")
        assertEquals(WifiSecurityType.EAP_ENTERPRISE, type)
        assertTrue(type.isSecure)
    }

    // -------------------------------------------------------------
    // 2. Wi-Fi Network Analyzer Tests (Bands & Channels)
    // -------------------------------------------------------------

    @Test
    fun testWifiChannelDerivation24Ghz() {
        assertEquals("2.4 GHz", WifiNetworkAnalyzer.getBand(2412))
        assertEquals(1, WifiNetworkAnalyzer.getChannel(2412))

        assertEquals("2.4 GHz", WifiNetworkAnalyzer.getBand(2437))
        assertEquals(6, WifiNetworkAnalyzer.getChannel(2437))

        assertEquals("2.4 GHz", WifiNetworkAnalyzer.getBand(2462))
        assertEquals(11, WifiNetworkAnalyzer.getChannel(2462))
    }

    @Test
    fun testWifiChannelDerivation5Ghz() {
        assertEquals("5 GHz", WifiNetworkAnalyzer.getBand(5180))
        assertEquals(36, WifiNetworkAnalyzer.getChannel(5180))

        assertEquals("5 GHz", WifiNetworkAnalyzer.getBand(5240))
        assertEquals(48, WifiNetworkAnalyzer.getChannel(5240))
    }

    @Test
    fun testWifiChannelDerivation6Ghz() {
        assertEquals("6 GHz (Wi-Fi 6E)", WifiNetworkAnalyzer.getBand(5975))
        assertEquals(5, WifiNetworkAnalyzer.getChannel(5975))
    }

    @Test
    fun testWifiSignalPercentageConversion() {
        assertEquals(100, WifiNetworkAnalyzer.calculateSignalPercent(-40))
        assertEquals(100, WifiNetworkAnalyzer.calculateSignalPercent(-50))
        assertEquals(60, WifiNetworkAnalyzer.calculateSignalPercent(-70))
        assertEquals(0, WifiNetworkAnalyzer.calculateSignalPercent(-100))
    }

    // -------------------------------------------------------------
    // 3. Network Fingerprint & Anomaly Detection Tests
    // -------------------------------------------------------------

    @Test
    fun testBaselineSaveAndMatch() = runBlocking {
        val net = WifiNetworkInfo(
            ssid = "HomeSecure_5G",
            bssid = "00:11:22:33:44:55",
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            frequencyMhz = 5180,
            channel = 36,
            band = "5 GHz",
            rssi = -55,
            signalPercent = 90,
            securityType = WifiSecurityType.WPA2_PSK,
            dnsServers = listOf("1.1.1.1", "1.0.0.1"),
            gateway = "192.168.1.1"
        )

        fingerprintManager.saveBaseline(net)
        val indicators = fingerprintManager.compareWithBaseline(net)
        assertTrue("No indicators expected for matching baseline", indicators.isEmpty())
    }

    @Test
    fun testBssidMismatchDetection() = runBlocking {
        val trustedNet = WifiNetworkInfo(
            ssid = "Office_Secure",
            bssid = "AA:BB:CC:DD:EE:01",
            capabilities = "[WPA3-SAE-CCMP][ESS]",
            frequencyMhz = 5180,
            channel = 36,
            band = "5 GHz",
            rssi = -60,
            signalPercent = 80,
            securityType = WifiSecurityType.WPA3_SAE
        )
        fingerprintManager.saveBaseline(trustedNet)

        // Rogue AP broadcasting same SSID with different BSSID
        val spoofedNet = trustedNet.copy(bssid = "AA:BB:CC:DD:EE:99")
        val indicators = fingerprintManager.compareWithBaseline(spoofedNet)

        assertTrue(indicators.any { it.type == IndicatorType.SSID_BSSID_MISMATCH })
        assertEquals(ThreatSeverity.HIGH, indicators.first { it.type == IndicatorType.SSID_BSSID_MISMATCH }.severity)
    }

    @Test
    fun testSecurityDowngradeDetection() = runBlocking {
        val trustedNet = WifiNetworkInfo(
            ssid = "CoffeeShop_VIP",
            bssid = "11:22:33:44:55:66",
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            frequencyMhz = 2412,
            channel = 1,
            band = "2.4 GHz",
            rssi = -65,
            signalPercent = 70,
            securityType = WifiSecurityType.WPA2_PSK
        )
        fingerprintManager.saveBaseline(trustedNet)

        // Downgraded to OPEN
        val downgradedNet = trustedNet.copy(
            capabilities = "[ESS]",
            securityType = WifiSecurityType.OPEN
        )
        val indicators = fingerprintManager.compareWithBaseline(downgradedNet)

        assertTrue(indicators.any { it.type == IndicatorType.SECURITY_DOWNGRADE })
    }

    @Test
    fun testSuspiciousNetworkDetectorAssessment() = runBlocking {
        val openRogueNet = WifiNetworkInfo(
            ssid = "Free_Public_Wifi",
            bssid = "DE:AD:BE:EF:00:01",
            capabilities = "[ESS]",
            frequencyMhz = 2412,
            channel = 1,
            band = "2.4 GHz",
            rssi = -50,
            signalPercent = 100,
            securityType = WifiSecurityType.OPEN,
            hasCaptivePortal = true
        )

        val assessment = suspiciousDetector.analyzeNetwork(openRogueNet)
        assertTrue(assessment.riskScore > 30)
        assertTrue(assessment.indicators.any { it.type == IndicatorType.OPEN_NETWORK_WARNING })
    }

    // -------------------------------------------------------------
    // 4. DNS Query Analyzer Tests
    // -------------------------------------------------------------

    @Test
    fun testPunycodeHomographDetection() {
        val result = dnsQueryAnalyzer.inspectDomain("xn--pple-43d.com")
        assertTrue(result.isSuspicious)
        assertTrue(result.detectedPatterns.contains("PUNYCODE_HOMOGRAPH"))
        assertTrue(result.reasons.any { it.contains("Punycode") })
    }

    @Test
    fun testHighAbuseTldDetection() {
        val result = dnsQueryAnalyzer.inspectDomain("free-crypto-giveaway.top")
        assertTrue(result.isSuspicious)
        assertTrue(result.reasons.any { it.contains("high-abuse top-level domain") })
    }

    @Test
    fun testLegitimateDomainClean() {
        val result = dnsQueryAnalyzer.inspectDomain("www.google.com")
        assertFalse(result.isSuspicious)
        assertTrue(result.riskScore < 20)
    }

    @Test
    fun testDnsMonitorRecording() = runBlocking {
        val event = dnsMonitor.recordDnsQuery("malicious-update-verify.top")
        assertNotNull(event)
        val recent = dnsMonitor.getRecentEvents(10)
        assertEquals(1, recent.size)
        assertEquals("malicious-update-verify.top", recent[0].queryDomain)
        assertTrue(recent[0].isFlagged)
    }

    // -------------------------------------------------------------
    // 5. Threat Intelligence Engine Tests
    // -------------------------------------------------------------

    @Test
    fun testDefaultRulesInitialization() = runBlocking {
        threatIntelligenceEngine.initializeDefaultRulesIfNeeded()
        val all = fakeThreatDao.getAllIndicatorsSync()
        assertTrue(all.isNotEmpty())
        assertTrue(all.any { it.indicatorValue == "c2-sample-beacon.org" })
    }

    @Test
    fun testExactThreatMatch() = runBlocking {
        threatIntelligenceEngine.initializeDefaultRulesIfNeeded()
        val verdict = threatIntelligenceEngine.evaluateIndicator("c2-sample-beacon.org")
        assertTrue(verdict is ThreatAnalysisVerdict.KnownThreatMatch)
        val match = verdict as ThreatAnalysisVerdict.KnownThreatMatch
        assertEquals("MALWARE_C2", match.indicator.threatCategory)
    }

    @Test
    fun testCsvThreatFeedImport() = runBlocking {
        val csv = """
            type,value,category,confidence
            DOMAIN,evil-phish.net,PHISHING,0.95
            IP,203.0.113.55,SCANNER,0.85
        """.trimIndent()

        val result = threatIntelligenceEngine.importer.importFromCsv(csv, "Community Feed")
        assertEquals(2, result.importedCount)

        val found = fakeThreatDao.findExactMatch("evil-phish.net")
        assertNotNull(found)
        assertEquals("PHISHING", found?.threatCategory)
    }

    // -------------------------------------------------------------
    // 6. Security Report Generation & Export Tests
    // -------------------------------------------------------------

    @Test
    fun testSecurityReportGeneratorFullAudit() {
        val wifi = WifiNetworkInfo(
            ssid = "Airport_Free_Wifi",
            bssid = "00:AA:BB:CC:DD:EE",
            capabilities = "[ESS]",
            frequencyMhz = 2437,
            channel = 6,
            band = "2.4 GHz",
            rssi = -60,
            signalPercent = 80,
            securityType = WifiSecurityType.OPEN,
            hasCaptivePortal = true
        )

        val assessment = NetworkSecurityAssessment(
            networkSsid = wifi.ssid,
            networkBssid = wifi.bssid,
            riskScore = 55,
            riskLevel = ThreatSeverity.MEDIUM,
            indicators = listOf(
                ThreatIndicator(
                    type = IndicatorType.OPEN_NETWORK_WARNING,
                    severity = ThreatSeverity.MEDIUM,
                    title = "Unencrypted Wireless Medium",
                    indicator = "Open ESS Beacon",
                    reason = "Communication is transmitted unencrypted.",
                    confidence = 1.0f,
                    recommendedAction = "Avoid unencrypted transmission.",
                    limitations = "Local scan indicator"
                )
            ),
            observations = listOf("Open Wi-Fi network detected")
        )

        val traffic = TrafficStatistics(
            isMonitoringActive = true,
            sessionStartTimeMs = 1000,
            bytesRx = 20000,
            bytesTx = 5000,
            totalPackets = 150,
            protocolBreakdown = mapOf("TCP" to 120L, "UDP" to 30L),
            topDestinations = mapOf("1.1.1.1" to 100L)
        )

        val report = reportGenerator.generateReport(
            connectedWifi = wifi,
            assessment = assessment,
            trafficStats = traffic,
            recentDnsEvents = emptyList(),
            threatIndicatorCount = 4
        )

        assertNotNull(report.reportId)
        assertTrue(report.reportId.startsWith("SEC-REP-"))
        assertEquals(55, report.overallRiskScore)
        assertEquals("MEDIUM", report.riskLevel)
        assertTrue(report.fullReportContent.contains("Airport_Free_Wifi"))
        assertTrue(report.fullReportContent.contains("Unencrypted Wireless Medium"))
        assertTrue(report.fullReportContent.contains("SAM CYBERSECURITY AUDIT REPORT"))

        // Export checks
        val md = ReportExporter.exportAsMarkdown(report)
        assertTrue(md.contains("# Security Audit: Airport_Free_Wifi"))
        assertTrue(md.contains("Risk Score"))

        val json = ReportExporter.exportAsJson(report)
        assertTrue(json.contains("\"overallRiskScore\": 55"))
    }

    // -------------------------------------------------------------
    // 7. Voice Intent Recognition Tests (Cybersecurity)
    // -------------------------------------------------------------

    @Test
    fun testIntentClassificationScanWifi() {
        val result = IntentClassifier.classify("scan wifi")
        assertEquals(CognitiveIntent.SCAN_WIFI, result.intent)

        val hinglish = IntentClassifier.classify("wifi scan karo")
        assertEquals(CognitiveIntent.SCAN_WIFI, hinglish.intent)

        val nearby = IntentClassifier.classify("show nearby networks")
        assertEquals(CognitiveIntent.SCAN_WIFI, nearby.intent)
    }

    @Test
    fun testIntentClassificationAnalyseNetwork() {
        val result = IntentClassifier.classify("is my wifi safe")
        assertEquals(CognitiveIntent.ANALYSE_NETWORK, result.intent)

        val audit = IntentClassifier.classify("run network security audit")
        assertEquals(CognitiveIntent.ANALYSE_NETWORK, audit.intent)

        val hinglish = IntentClassifier.classify("wifi surakshit hai kya")
        assertEquals(CognitiveIntent.ANALYSE_NETWORK, hinglish.intent)
    }
}

// =====================================================================
// IN-MEMORY FAKE DAOS FOR TEST VERIFICATION
// =====================================================================

class FakeNetworkFingerprintDao : NetworkFingerprintDao {
    private val fingerprints = mutableMapOf<String, NetworkFingerprintEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertFingerprint(fingerprint: NetworkFingerprintEntity): Long {
        val id = idGen.getAndIncrement()
        val entity = fingerprint.copy(id = id)
        fingerprints[entity.bssid] = entity
        return id
    }

    override suspend fun updateFingerprint(fingerprint: NetworkFingerprintEntity) {
        fingerprints[fingerprint.bssid] = fingerprint
    }

    override suspend fun deleteFingerprint(fingerprint: NetworkFingerprintEntity) {
        fingerprints.remove(fingerprint.bssid)
    }

    override suspend fun getFingerprintByBssid(bssid: String): NetworkFingerprintEntity? = fingerprints[bssid]

    override suspend fun getFingerprintsBySsid(ssid: String): List<NetworkFingerprintEntity> {
        return fingerprints.values.filter { it.ssid.equals(ssid, ignoreCase = true) }
    }

    override fun getAllFingerprints(): Flow<List<NetworkFingerprintEntity>> = flowOf(fingerprints.values.toList())

    override suspend fun getAllFingerprintsSync(): List<NetworkFingerprintEntity> = fingerprints.values.toList()

    override suspend fun deleteByBssid(bssid: String): Int {
        return if (fingerprints.remove(bssid) != null) 1 else 0
    }

    override suspend fun clearAll() {
        fingerprints.clear()
    }
}

class FakeTrafficStatisticsDao : TrafficStatisticsDao {
    private val sessions = mutableListOf<TrafficStatisticsEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertSession(session: TrafficStatisticsEntity): Long {
        val id = idGen.getAndIncrement()
        sessions.add(session.copy(id = id))
        return id
    }

    override fun getAllSessions(): Flow<List<TrafficStatisticsEntity>> = flowOf(sessions.toList())

    override suspend fun getLatestSession(): TrafficStatisticsEntity? = sessions.lastOrNull()

    override suspend fun clearAll() {
        sessions.clear()
    }
}

class FakeDnsEventDao : DnsEventDao {
    private val events = mutableListOf<DnsEventEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertEvent(event: DnsEventEntity): Long {
        val id = idGen.getAndIncrement()
        events.add(event.copy(id = id))
        return id
    }

    override suspend fun insertEvents(newEvents: List<DnsEventEntity>) {
        newEvents.forEach { insertEvent(it) }
    }

    override fun getAllEvents(): Flow<List<DnsEventEntity>> = flowOf(events.toList())

    override suspend fun getRecentEvents(limit: Int): List<DnsEventEntity> = events.takeLast(limit)

    override suspend fun getFlaggedEvents(): List<DnsEventEntity> = events.filter { it.isFlagged }

    override suspend fun clearAll() {
        events.clear()
    }
}

class FakeThreatIndicatorDao : ThreatIndicatorDao {
    private val indicators = mutableListOf<ThreatIndicatorEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertIndicator(indicator: ThreatIndicatorEntity): Long {
        val id = idGen.getAndIncrement()
        indicators.add(indicator.copy(id = id))
        return id
    }

    override suspend fun insertIndicators(newIndicators: List<ThreatIndicatorEntity>) {
        newIndicators.forEach {
            val id = idGen.getAndIncrement()
            indicators.add(it.copy(id = id))
        }
    }

    override suspend fun deleteIndicator(indicator: ThreatIndicatorEntity) {
        indicators.removeAll { it.id == indicator.id }
    }

    override fun getAllIndicators(): Flow<List<ThreatIndicatorEntity>> = flowOf(indicators.toList())

    override suspend fun getAllIndicatorsSync(): List<ThreatIndicatorEntity> = indicators.toList()

    override suspend fun findExactMatch(value: String): ThreatIndicatorEntity? {
        return indicators.firstOrNull { it.indicatorValue.equals(value, ignoreCase = true) }
    }

    override suspend fun searchIndicators(query: String): List<ThreatIndicatorEntity> {
        return indicators.filter { it.indicatorValue.contains(query, ignoreCase = true) }
    }

    override suspend fun deleteByFeed(feedName: String): Int {
        val before = indicators.size
        indicators.removeAll { it.sourceFeed == feedName }
        return before - indicators.size
    }

    override suspend fun clearAll() {
        indicators.clear()
    }
}

class FakeSecurityReportDao : SecurityReportDao {
    private val reports = mutableListOf<SecurityReportEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertReport(report: SecurityReportEntity): Long {
        val id = idGen.getAndIncrement()
        reports.add(report.copy(id = id))
        return id
    }

    override fun getAllReports(): Flow<List<SecurityReportEntity>> = flowOf(reports.toList())

    override suspend fun getReportById(reportId: String): SecurityReportEntity? {
        return reports.firstOrNull { it.reportId == reportId }
    }

    override suspend fun deleteById(reportId: String): Int {
        val removed = reports.removeAll { it.reportId == reportId }
        return if (removed) 1 else 0
    }

    override suspend fun clearAll() {
        reports.clear()
    }
}

class FakeSecurityEventDao : SecurityEventDao {
    private val events = mutableListOf<SecurityEventEntity>()
    private val idGen = AtomicLong(1)

    override suspend fun insertEvent(event: SecurityEventEntity): Long {
        val id = idGen.getAndIncrement()
        events.add(event.copy(id = id))
        return id
    }

    override fun getAllEvents(): Flow<List<SecurityEventEntity>> = flowOf(events.toList())

    override suspend fun getRecentEvents(limit: Int): List<SecurityEventEntity> = events.takeLast(limit)

    override suspend fun deleteById(id: Long) {
        events.removeAll { it.id == id }
    }

    override suspend fun clearAll() {
        events.clear()
    }
}
