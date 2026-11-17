package com.sam.assistant.cybersecurity.threat_detection

import com.sam.assistant.cybersecurity.wifi.WifiNetworkInfo
import com.sam.assistant.cybersecurity.wifi.WifiSecurityType

class NetworkFingerprintManager(
    private val fingerprintDao: NetworkFingerprintDao? = null
) {

    /**
     * Saves a network as a trusted baseline fingerprint.
     */
    suspend fun saveBaseline(network: WifiNetworkInfo, isTrusted: Boolean = true): Long =
        saveBaselineFingerprint(network, isTrusted)

    suspend fun saveBaselineFingerprint(network: WifiNetworkInfo, isTrusted: Boolean = true): Long {
        val existing = fingerprintDao?.getFingerprintByBssid(network.bssid)
        val entity = NetworkFingerprintEntity(
            id = existing?.id ?: 0,
            ssid = network.ssid,
            bssid = network.bssid,
            securityType = network.securityType.name,
            capabilities = network.capabilities,
            frequencyMhz = network.frequencyMhz,
            gatewayIp = network.gatewayIp,
            dnsServers = network.dnsServers.joinToString(","),
            isTrusted = isTrusted,
            firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )
        return fingerprintDao?.insertFingerprint(entity) ?: 0L
    }

    /**
     * Compares an observed network against locally saved baseline fingerprints.
     */
    suspend fun compareWithBaseline(network: WifiNetworkInfo): List<ThreatIndicator> {
        val indicators = mutableListOf<ThreatIndicator>()
        val savedFingerprints = fingerprintDao?.getFingerprintsBySsid(network.ssid) ?: emptyList()

        if (savedFingerprints.isEmpty()) {
            // No baseline recorded yet
            if (network.securityType == WifiSecurityType.OPEN) {
                indicators.add(
                    ThreatIndicator(
                        type = IndicatorType.OPEN_NETWORK_WARNING,
                        severity = ThreatSeverity.MEDIUM,
                        title = "Unencrypted Open Wireless Network",
                        indicator = "Open Wi-Fi without 802.11 encryption",
                        reason = "Packets transmitted across open Wi-Fi can be observed by any nearby receiver.",
                        confidence = 0.99f,
                        recommendedAction = "Avoid entering sensitive credentials or enable SAM VPN monitoring.",
                        limitations = "Public hotspots (airports, cafes) commonly operate open access points."
                    )
                )
            }
            return indicators
        }

        // 1. Check BSSID matching
        val matchingBssid = savedFingerprints.find { it.bssid.equals(network.bssid, ignoreCase = true) }
        val trustedBaseline = savedFingerprints.firstOrNull { it.isTrusted } ?: savedFingerprints.first()

        if (matchingBssid == null) {
            indicators.add(
                ThreatIndicator(
                    type = IndicatorType.SSID_BSSID_MISMATCH,
                    severity = ThreatSeverity.HIGH,
                    title = "BSSID Mismatch on Known Network",
                    indicator = "Observed MAC: ${network.bssid} | Baseline MAC: ${trustedBaseline.bssid}",
                    reason = "A known Wi-Fi network name (\"${network.ssid}\") is broadcasting from an access point hardware address that differs from your saved baseline.",
                    confidence = 0.85f,
                    recommendedAction = "Verify whether this venue operates a multi-AP mesh or if an unauthorized hotspot is imitating the network.",
                    limitations = "Enterprise or mesh networks legitimately use multiple physical access points under one SSID."
                )
            )
        }

        // 2. Check Security Standard Downgrade
        val baselineSecurity = try {
            WifiSecurityType.valueOf(trustedBaseline.securityType)
        } catch (e: Exception) {
            WifiSecurityType.UNKNOWN
        }

        val isDowngraded = baselineSecurity.isSecure && !network.securityType.isSecure
        if (isDowngraded) {
            indicators.add(
                ThreatIndicator(
                    type = IndicatorType.SECURITY_DOWNGRADE,
                    severity = ThreatSeverity.CRITICAL,
                    title = "Severe Security Downgrade Detected",
                    indicator = "Baseline: ${baselineSecurity.displayName} $\\to$ Current: ${network.securityType.displayName}",
                    reason = "This network was previously saved with encrypted protection (${baselineSecurity.displayName}), but is currently broadcasting with weak or zero encryption.",
                    confidence = 0.95f,
                    recommendedAction = "Disconnect immediately. Do not communicate sensitive information across a downgraded network.",
                    limitations = "May occur if a network administrator reset router configurations to factory defaults."
                )
            )
        }

        // 3. Check DNS deviations
        if (network.dnsServers.isNotEmpty() && trustedBaseline.dnsServers.isNotBlank()) {
            val baselineDnsList = trustedBaseline.dnsServers.split(",").map { it.trim() }
            val hasCommonDns = network.dnsServers.any { baselineDnsList.contains(it) }
            if (!hasCommonDns) {
                indicators.add(
                    ThreatIndicator(
                        type = IndicatorType.UNUSUAL_DNS,
                        severity = ThreatSeverity.MEDIUM,
                        title = "DNS Server Configuration Changed",
                        indicator = "Observed DNS: ${network.dnsServers.joinToString(", ")} | Baseline: ${trustedBaseline.dnsServers}",
                        reason = "Primary DNS server IP address differs from the saved baseline for this network.",
                        confidence = 0.70f,
                        recommendedAction = "Check whether Private DNS or router DHCP configuration was recently altered.",
                        limitations = "DHCP servers dynamically rotate secondary and primary upstream DNS IPs."
                    )
                )
            }
        }

        return indicators
    }

    suspend fun getBaseline(bssid: String): NetworkFingerprintEntity? {
        return fingerprintDao?.getFingerprintByBssid(bssid)
    }

    suspend fun deleteBaseline(bssid: String): Boolean {
        return (fingerprintDao?.deleteByBssid(bssid) ?: 0) > 0
    }
}
