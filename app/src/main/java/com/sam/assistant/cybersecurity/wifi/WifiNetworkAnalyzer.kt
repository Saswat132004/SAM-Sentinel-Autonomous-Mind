package com.sam.assistant.cybersecurity.wifi

class WifiNetworkAnalyzer(
    private val securityClassifier: WifiSecurityClassifier = WifiSecurityClassifier()
) {

    companion object {
        fun getBand(frequencyMhz: Int): String = WifiNetworkAnalyzer().determineBand(frequencyMhz)
        fun getChannel(frequencyMhz: Int): Int = WifiNetworkAnalyzer().deriveChannel(frequencyMhz)
        fun calculateSignalPercent(rssiDbm: Int): Int = WifiNetworkAnalyzer().calculateSignalQuality(rssiDbm).first
    }

    /**
     * Derives Wi-Fi channel number from frequency in MHz.
     */
    fun deriveChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5925..7125 -> (frequencyMhz - 5950) / 5
            else -> 0
        }
    }

    /**
     * Determines Wi-Fi frequency band label.
     */
    fun determineBand(frequencyMhz: Int): String {
        return when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 5000..5900 -> "5 GHz"
            frequencyMhz in 5925..7125 -> "6 GHz (Wi-Fi 6E)"
            else -> "Unknown Band"
        }
    }

    /**
     * Calculates signal quality percentage and human-readable label from RSSI (dBm).
     */
    fun calculateSignalQuality(rssiDbm: Int): Pair<Int, String> {
        val percent = when {
            rssiDbm >= -50 -> 100
            rssiDbm <= -100 -> 0
            else -> 2 * (rssiDbm + 100)
        }.coerceIn(0, 100)

        val quality = when {
            rssiDbm >= -60 -> "Excellent"
            rssiDbm >= -70 -> "Good"
            rssiDbm >= -80 -> "Fair"
            else -> "Weak"
        }

        return Pair(percent, quality)
    }

    /**
     * Compiles complete WifiNetworkInfo from raw scan parameters.
     */
    fun buildNetworkInfo(
        ssid: String,
        bssid: String,
        rssi: Int,
        frequencyMhz: Int,
        capabilities: String,
        isConnected: Boolean = false,
        ipAddress: String? = null,
        gateway: String? = null,
        dnsServers: List<String> = emptyList()
    ): WifiNetworkInfo {
        val cleanSsid = if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid.substring(1, ssid.length - 1)
        } else {
            ssid
        }

        val securityType = securityClassifier.classifySecurity(capabilities)
        val channel = deriveChannel(frequencyMhz)
        val band = determineBand(frequencyMhz)
        val (signalPercent, signalQuality) = calculateSignalQuality(rssi)
        val observations = securityClassifier.generateObservations(securityType, capabilities)

        return WifiNetworkInfo(
            ssid = if (cleanSsid.isBlank()) "<Hidden Network>" else cleanSsid,
            bssid = bssid.uppercase(),
            rssi = rssi,
            frequencyMhz = frequencyMhz,
            channel = channel,
            band = band,
            securityType = securityType,
            capabilities = capabilities,
            isConnected = isConnected,
            signalPercent = signalPercent,
            signalQuality = signalQuality,
            ipAddress = ipAddress,
            gateway = gateway,
            dnsServers = dnsServers,
            securityObservations = observations
        )
    }
}
