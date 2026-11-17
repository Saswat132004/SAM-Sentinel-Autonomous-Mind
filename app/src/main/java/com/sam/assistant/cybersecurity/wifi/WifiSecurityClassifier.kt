package com.sam.assistant.cybersecurity.wifi

class WifiSecurityClassifier {

    companion object {
        fun classifySecurity(capabilities: String): WifiSecurityType =
            WifiSecurityClassifier().classifySecurity(capabilities)
    }

    /**
     * Classifies Wi-Fi security standard from an 802.11 beacon capability string.
     */
    fun classifySecurity(capabilities: String): WifiSecurityType {
        val upper = capabilities.uppercase()
        return when {
            upper.contains("SAE") || upper.contains("WPA3") -> WifiSecurityType.WPA3_SAE
            upper.contains("EAP") -> WifiSecurityType.EAP_ENTERPRISE
            upper.contains("WPA2") || upper.contains("RSN") -> WifiSecurityType.WPA2_PSK
            upper.contains("WPA") -> WifiSecurityType.WPA
            upper.contains("WEP") -> WifiSecurityType.WEP
            !upper.contains("WPA") && !upper.contains("WEP") && !upper.contains("RSN") -> WifiSecurityType.OPEN
            else -> WifiSecurityType.UNKNOWN
        }
    }

    /**
     * Generates security observations for a given configuration.
     */
    fun generateObservations(securityType: WifiSecurityType, capabilities: String): List<String> {
        val observations = mutableListOf<String>()

        when (securityType) {
            WifiSecurityType.OPEN -> {
                observations.add("Open network: Transmissions are unencrypted over the air. Susceptible to passive wireless packet capture.")
                observations.add("Recommendation: Use a trusted VPN or avoid accessing sensitive accounts on open networks.")
            }
            WifiSecurityType.WEP -> {
                observations.add("Deprecated WEP protocol detected: Exploitable RC4 stream cipher vulnerabilities allow rapid key recovery.")
                observations.add("Recommendation: Upgrade access point to WPA2 or WPA3 immediately.")
            }
            WifiSecurityType.WPA -> {
                observations.add("Legacy WPA (TKIP) detected: Deprecated protocol susceptible to Beck-Tews and Ohigashi-Kurokawa attacks.")
            }
            WifiSecurityType.WPA2_PSK -> {
                observations.add("WPA2 Personal (AES-CCMP): Standard robust encryption.")
                if (capabilities.contains("TKIP")) {
                    observations.add("Notice: Mixed-mode TKIP/CCMP enabled. Disabling TKIP improves security posture.")
                }
            }
            WifiSecurityType.WPA3_SAE -> {
                observations.add("WPA3 Personal (SAE): Modern standard with forward secrecy and protected management frames (PMF).")
            }
            WifiSecurityType.EAP_ENTERPRISE -> {
                observations.add("Enterprise 802.1X authentication: Uses centralized RADIUS authentication with individual credentials.")
            }
            WifiSecurityType.UNKNOWN -> {
                observations.add("Unknown security profile: Inspect capabilities string manually.")
            }
        }

        if (capabilities.contains("WPS")) {
            observations.add("Wi-Fi Protected Setup (WPS) detected: WPS PIN mode may be vulnerable to offline brute-force attacks.")
        }

        return observations
    }
}
