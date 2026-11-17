package com.sam.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.assistant.SamApplication
import com.sam.assistant.cybersecurity.dns.DnsEventEntity
import com.sam.assistant.cybersecurity.reports.ReportExporter
import com.sam.assistant.cybersecurity.reports.SecurityReportEntity
import com.sam.assistant.cybersecurity.threat_detection.NetworkSecurityAssessment
import com.sam.assistant.cybersecurity.threat_detection.ThreatSeverity
import com.sam.assistant.cybersecurity.traffic.VpnMonitorStatus
import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo
import com.sam.assistant.ui.components.GlassCard
import com.sam.assistant.ui.components.StatusBadge
import com.sam.assistant.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CybersecurityDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SamApplication
    val cybersecurityEngine = remember { app.cybersecurityEngine }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Network Audit", "Wi-Fi Scanner", "Traffic & DNS", "Reports & Intel")

    // State
    var currentWifi by remember { mutableStateOf<WifiNetworkInfo?>(null) }
    var currentAssessment by remember { mutableStateOf<NetworkSecurityAssessment?>(null) }
    var isAuditing by remember { mutableStateOf(false) }

    val nearbyNetworks by cybersecurityEngine.wifiScanManager.scanResults.collectAsState()
    val isScanning by cybersecurityEngine.wifiScanManager.isScanning.collectAsState()
    val cooldownSeconds by cybersecurityEngine.wifiScanManager.cooldownSecondsRemaining.collectAsState()

    val vpnStatus by cybersecurityEngine.networkMonitor.status.collectAsState()
    val trafficStats by cybersecurityEngine.networkMonitor.liveStats.collectAsState()

    val recentReports by (cybersecurityEngine.securityReportDao?.getAllReports() ?: kotlinx.coroutines.flow.emptyFlow())
        .collectAsState(initial = emptyList())
    val recentDnsEvents by (cybersecurityEngine.dnsEventDao?.getAllEvents() ?: kotlinx.coroutines.flow.emptyFlow())
        .collectAsState(initial = emptyList())

    // Dialog state for full report viewing
    var viewingReport by remember { mutableStateOf<SecurityReportEntity?>(null) }

    // Domain test state
    var domainInput by remember { mutableStateOf("") }
    var domainResult by remember { mutableStateOf<DnsEventEntity?>(null) }

    // Load initial network state
    LaunchedEffect(Unit) {
        cybersecurityEngine.initializeDefaults()
        currentWifi = cybersecurityEngine.wifiScanManager.getCurrentConnection()
        currentWifi?.let {
            currentAssessment = cybersecurityEngine.assessNetwork(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cybersecurity Intelligence", style = SamTypography.titleLarge.copy(fontSize = 18.sp))
                        Text("realme P4 5G • Stage 7 Local Audit Engine", style = SamTypography.labelSmall.copy(color = SamSecondaryText))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SamText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SamDarkBackground)
            )
        },
        containerColor = SamDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SamDarkSurface,
                contentColor = SamPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) SamPrimary else SamSecondaryText
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> NetworkAuditTab(
                    currentWifi = currentWifi,
                    assessment = currentAssessment,
                    isAuditing = isAuditing,
                    onRunAudit = {
                        scope.launch {
                            isAuditing = true
                            try {
                                val report = cybersecurityEngine.auditCurrentNetwork()
                                currentWifi = cybersecurityEngine.wifiScanManager.getCurrentConnection()
                                currentAssessment = currentWifi?.let { cybersecurityEngine.assessNetwork(it) }
                                viewingReport = report
                                Toast.makeText(context, "Audit complete. Risk score: ${report.overallRiskScore}/100", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Audit error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isAuditing = false
                            }
                        }
                    },
                    onSaveBaseline = {
                        currentWifi?.let { wifi ->
                            scope.launch {
                                cybersecurityEngine.saveNetworkBaseline(wifi)
                                Toast.makeText(context, "Saved baseline for ${wifi.ssid}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                1 -> WifiScannerTab(
                    networks = nearbyNetworks,
                    isScanning = isScanning,
                    cooldown = cooldownSeconds,
                    onStartScan = {
                        scope.launch {
                            cybersecurityEngine.scanNearbyWifi()
                        }
                    },
                    onSelectNetwork = { network ->
                        scope.launch {
                            val assess = cybersecurityEngine.assessNetwork(network)
                            Toast.makeText(context, "${network.ssid}: Risk ${assess.riskScore}/100 (${assess.riskLevel.displayName})", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                2 -> TrafficAndDnsTab(
                    vpnStatus = vpnStatus,
                    trafficStats = trafficStats,
                    recentDns = recentDnsEvents,
                    domainInput = domainInput,
                    domainResult = domainResult,
                    onDomainInputChange = { domainInput = it },
                    onInspectDomain = {
                        if (domainInput.isNotBlank()) {
                            scope.launch {
                                domainResult = cybersecurityEngine.inspectDomain(domainInput.trim())
                            }
                        }
                    },
                    onToggleVpn = {
                        if (vpnStatus is VpnMonitorStatus.Active) {
                            scope.launch {
                                cybersecurityEngine.networkMonitor.stopMonitoring()
                                Toast.makeText(context, "Traffic monitor stopped", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val intent = cybersecurityEngine.networkMonitor.getConsentIntent()
                            if (intent != null) {
                                Toast.makeText(context, "VPN permission required. Start via Settings/Permission screen.", Toast.LENGTH_LONG).show()
                            } else {
                                cybersecurityEngine.networkMonitor.startMonitoring()
                                Toast.makeText(context, "Traffic monitor active", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                3 -> ReportsAndIntelTab(
                    reports = recentReports,
                    onViewReport = { viewingReport = it },
                    onResetDefaults = {
                        scope.launch {
                            cybersecurityEngine.initializeDefaults()
                            Toast.makeText(context, "Threat intelligence defaults refreshed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // View Full Report Dialog
    viewingReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewingReport = null },
            title = {
                Text(report.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text(
                            text = report.fullReportContent,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = SamText
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SAM Report", ReportExporter.exportAsMarkdown(report)))
                    Toast.makeText(context, "Report copied as Markdown", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy MD", color = SamPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingReport = null }) {
                    Text("Close", color = SamSecondaryText)
                }
            },
            containerColor = SamDarkSurface
        )
    }
}

@Composable
fun NetworkAuditTab(
    currentWifi: WifiNetworkInfo?,
    assessment: NetworkSecurityAssessment?,
    isAuditing: Boolean,
    onRunAudit: () -> Unit,
    onSaveBaseline: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Wi-Fi Connection", style = SamTypography.titleMedium.copy(color = SamSecondaryText))
                        Text(
                            text = currentWifi?.ssid ?: "No Wi-Fi Connection",
                            style = SamTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    if (currentWifi != null) {
                        StatusBadge(
                            text = currentWifi.securityType.displayName,
                            color = if (currentWifi.securityType.isSecure) SamSuccess else SamError
                        )
                    }
                }

                if (currentWifi != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("BSSID: ${currentWifi.bssid}", fontSize = 12.sp, color = SamSecondaryText)
                    Text("Band: ${currentWifi.band} • Channel ${currentWifi.channel} (${currentWifi.frequencyMhz} MHz)", fontSize = 12.sp, color = SamSecondaryText)
                    Text("Signal: ${currentWifi.rssiDbm} dBm (${currentWifi.signalLevelPercent}%) • Link: ${currentWifi.linkSpeedMbps} Mbps", fontSize = 12.sp, color = SamSecondaryText)
                    Text("IP: ${currentWifi.ipAddress ?: "N/A"} • Gateway: ${currentWifi.gatewayIp ?: "N/A"}", fontSize = 12.sp, color = SamSecondaryText)
                }
            }
        }

        // Risk Gauge Card
        item {
            val score = assessment?.riskScore ?: 0
            val severity = assessment?.riskLevel ?: ThreatSeverity.LOW
            val badgeColor = when (severity) {
                ThreatSeverity.LOW -> SamSuccess
                ThreatSeverity.MEDIUM -> SamWarning
                ThreatSeverity.HIGH -> Color(0xFFFF6D00)
                ThreatSeverity.CRITICAL -> SamError
            }

            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Network Risk Score", style = SamTypography.titleMedium.copy(color = SamSecondaryText))
                        Text("$score / 100", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                    StatusBadge(text = severity.displayName.uppercase(), color = badgeColor)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = badgeColor,
                    trackColor = SamCardBorder
                )
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunAudit,
                    enabled = !isAuditing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary)
                ) {
                    if (isAuditing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isAuditing) "Auditing..." else "Run Deep Audit")
                }

                OutlinedButton(
                    onClick = onSaveBaseline,
                    enabled = currentWifi != null,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, SamPrimary)
                ) {
                    Text("Trust Baseline", color = SamPrimary)
                }
            }
        }

        // Threat Indicators List
        item {
            Text("Observed Indicators (${assessment?.indicators?.size ?: 0})", style = SamTypography.titleMedium)
        }

        val indicators = assessment?.indicators ?: emptyList()
        if (indicators.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SamSuccess.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, SamSuccess.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SamSuccess)
                        Text("No threat indicators or suspicious anomalies observed.", color = SamSuccess, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(indicators) { ind ->
                val color = when (ind.severity) {
                    ThreatSeverity.LOW -> SamSuccess
                    ThreatSeverity.MEDIUM -> SamWarning
                    ThreatSeverity.HIGH -> Color(0xFFFF6D00)
                    ThreatSeverity.CRITICAL -> SamError
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SamDarkSurface,
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(ind.title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
                            Text("${(ind.confidence * 100).toInt()}% Conf.", fontSize = 11.sp, color = SamSecondaryText)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(ind.reason, fontSize = 12.sp, color = SamText)
                        if (ind.recommendedAction.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Action: ${ind.recommendedAction}", fontSize = 11.sp, color = SamPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiScannerTab(
    networks: List<WifiNetworkInfo>,
    isScanning: Boolean,
    cooldown: Int,
    onStartScan: () -> Unit,
    onSelectNetwork: (WifiNetworkInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Nearby Wi-Fi Beacons", style = SamTypography.titleLarge.copy(fontSize = 18.sp))
                    Text(
                        if (cooldown > 0) "Throttling cooldown: ${cooldown}s" else "Scanned ${networks.size} networks",
                        fontSize = 12.sp,
                        color = if (cooldown > 0) SamWarning else SamSecondaryText
                    )
                }

                Button(
                    onClick = onStartScan,
                    enabled = !isScanning && cooldown == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = SamPrimary)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(if (isScanning) "Scanning..." else "Scan APs")
                }
            }
        }

        if (networks.isEmpty()) {
            item {
                GlassCard {
                    Text("No nearby networks detected. Click 'Scan APs' to discover surrounding access points.", color = SamSecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            items(networks) { net ->
                GlassCard(
                    modifier = Modifier.clickable { onSelectNetwork(net) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(net.ssid, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SamText)
                            Text("BSSID: ${net.bssid}", fontSize = 11.sp, color = SamSecondaryText)
                            Text("${net.band} • Ch ${net.channel} (${net.frequencyMhz} MHz) • ${net.rssiDbm} dBm", fontSize = 11.sp, color = SamSecondaryText)
                        }
                        StatusBadge(
                            text = net.securityType.displayName,
                            color = if (net.securityType.isSecure) SamSuccess else SamError
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrafficAndDnsTab(
    vpnStatus: VpnMonitorStatus,
    trafficStats: com.sam.assistant.cybersecurity.traffic.TrafficStatistics?,
    recentDns: List<DnsEventEntity>,
    domainInput: String,
    domainResult: DnsEventEntity?,
    onDomainInputChange: (String) -> Unit,
    onInspectDomain: () -> Unit,
    onToggleVpn: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // VPN Section
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Local Traffic Monitor (VPN)", style = SamTypography.titleMedium.copy(color = SamSecondaryText))
                        Text(
                            when (vpnStatus) {
                                is VpnMonitorStatus.Active -> "Monitoring Active"
                                is VpnMonitorStatus.Inactive -> "Stopped"
                                is VpnMonitorStatus.ConsentRequired -> "Consent Required"
                                is VpnMonitorStatus.Error -> "Error: ${(vpnStatus as VpnMonitorStatus.Error).message}"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (vpnStatus is VpnMonitorStatus.Active) SamSuccess else SamWarning
                        )
                    }

                    Button(
                        onClick = onToggleVpn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vpnStatus is VpnMonitorStatus.Active) SamError else SamPrimary
                        )
                    ) {
                        Text(if (vpnStatus is VpnMonitorStatus.Active) "Stop" else "Start")
                    }
                }

                if (trafficStats != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Packets: ${trafficStats.totalPackets} (Rx: ${trafficStats.bytesRx / 500}, Tx: ${trafficStats.bytesTx / 500})", fontSize = 12.sp, color = SamSecondaryText)
                    Text("Traffic: ${trafficStats.bytesRx + trafficStats.bytesTx} B (Rx: ${trafficStats.bytesRx} B, Tx: ${trafficStats.bytesTx} B)", fontSize = 12.sp, color = SamSecondaryText)
                    Text("Protocols: ${trafficStats.protocolBreakdown}", fontSize = 12.sp, color = SamSecondaryText)
                    Text("Observed Remote IPs: ${trafficStats.topDestinations.keys.size}", fontSize = 12.sp, color = SamSecondaryText)
                }
            }
        }

        // Offline DNS Domain Inspector
        item {
            GlassCard {
                Text("Offline DNS & Phishing Analyzer", style = SamTypography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = onDomainInputChange,
                        placeholder = { Text("e.g. login-bank.tk", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamPrimary,
                            unfocusedBorderColor = SamCardBorder,
                            focusedTextColor = SamText,
                            unfocusedTextColor = SamText
                        )
                    )
                    Button(
                        onClick = onInspectDomain,
                        colors = ButtonDefaults.buttonColors(containerColor = SamPrimary)
                    ) {
                        Text("Analyze")
                    }
                }

                domainResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (res.isFlagged) SamError.copy(alpha = 0.15f) else SamSuccess.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (res.isFlagged) SamError.copy(alpha = 0.4f) else SamSuccess.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Result: ${if (res.isFlagged) "SUSPICIOUS" else "CLEAN"}",
                                fontWeight = FontWeight.Bold,
                                color = if (res.isFlagged) SamError else SamSuccess
                            )
                            Text("Domain: ${res.queryDomain} • Type: ${res.queryType}", fontSize = 12.sp, color = SamText)
                            if (res.riskReason?.isNotBlank() == true) {
                                Text("Reasons: ${res.riskReason}", fontSize = 11.sp, color = SamSecondaryText)
                            }
                        }
                    }
                }
            }
        }

        // Recent DNS Queries Log
        item {
            Text("Recent DNS Queries (${recentDns.size})", style = SamTypography.titleMedium)
        }

        if (recentDns.isEmpty()) {
            item {
                Text("No DNS events logged yet.", color = SamSecondaryText, fontSize = 12.sp)
            }
        } else {
            items(recentDns.take(15)) { dns ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dns.queryDomain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SamText)
                            Text(dns.riskReason ?: "Clean lookup (${dns.queryType})", fontSize = 11.sp, color = SamSecondaryText)
                        }
                        StatusBadge(
                            text = if (dns.isFlagged) "ALERT" else "OK",
                            color = if (dns.isFlagged) SamError else SamSuccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsAndIntelTab(
    reports: List<SecurityReportEntity>,
    onViewReport: (SecurityReportEntity) -> Unit,
    onResetDefaults: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Security Audits", style = SamTypography.titleLarge.copy(fontSize = 18.sp))
                OutlinedButton(
                    onClick = onResetDefaults,
                    border = BorderStroke(1.dp, SamPrimary)
                ) {
                    Text("Reload Intel", color = SamPrimary, fontSize = 12.sp)
                }
            }
        }

        if (reports.isEmpty()) {
            item {
                GlassCard {
                    Text("No reports generated yet. Run a Deep Audit from the Network Audit tab.", color = SamSecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            items(reports) { rep ->
                GlassCard(
                    modifier = Modifier.clickable { onViewReport(rep) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rep.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SamText)
                            Text("ID: ${rep.reportId} • Risk: ${rep.overallRiskScore}/100", fontSize = 12.sp, color = SamSecondaryText)
                            Text(rep.summaryText, fontSize = 11.sp, color = SamSecondaryText, maxLines = 1)
                        }
                        StatusBadge(
                            text = rep.riskLevel,
                            color = when (rep.riskLevel) {
                                "LOW" -> SamSuccess
                                "MEDIUM" -> SamWarning
                                else -> SamError
                            }
                        )
                    }
                }
            }
        }
    }
}
