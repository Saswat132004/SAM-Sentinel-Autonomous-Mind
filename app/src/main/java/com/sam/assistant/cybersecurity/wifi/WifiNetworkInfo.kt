package com.sam.assistant.cybersecurity.wifi

enum class WifiSecurityType(val displayName: String, val isSecure: Boolean) {
    OPEN("Open (Unencrypted)", false),
    WEP("WEP (Deprecated / Insecure)", false),
    WPA("WPA (Legacy / Weak)", false),
    WPA2_PSK("WPA2 Personal (AES-CCMP)", true),
    WPA3_SAE("WPA3 Personal (SAE)", true),
    EAP_ENTERPRISE("WPA/WPA2/WPA3 Enterprise (802.1X)", true),
    UNKNOWN("Unknown Configuration", false)
}

data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String, // "2.4 GHz", "5 GHz", "6 GHz"
    val securityType: WifiSecurityType,
    val capabilities: String,
    val isConnected: Boolean = false,
    val signalPercent: Int = 0,
    val signalQuality: String = "Unknown",
    val ipAddress: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val securityObservations: List<String> = emptyList(),
    val linkSpeedMbps: Int = 0,
    val hasCaptivePortal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val rssiDbm: Int get() = rssi
    val signalLevelPercent: Int get() = signalPercent
    val gatewayIp: String? get() = gateway
}
