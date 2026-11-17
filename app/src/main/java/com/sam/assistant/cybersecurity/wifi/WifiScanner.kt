package com.sam.assistant.cybersecurity.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address

sealed class WifiScanResult {
    data class Success(
        val connectedNetwork: WifiNetworkInfo?,
        val nearbyNetworks: List<WifiNetworkInfo>,
        val isThrottled: Boolean = false,
        val message: String = "Wi-Fi scan completed successfully."
    ) : WifiScanResult()

    data class PermissionRequired(
        val missingPermissions: List<String>,
        val explanation: String
    ) : WifiScanResult()

    data class Error(val errorReason: String) : WifiScanResult()
}

class WifiScanner(
    private val context: Context,
    private val analyzer: WifiNetworkAnalyzer = WifiNetworkAnalyzer()
) {
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var lastScanTimestamp: Long = 0
    private var cachedScanResult: WifiScanResult.Success? = null
    private val SCAN_COOLDOWN_MS = 15000L // 15s cache to respect Android 10+ scan throttling

    /**
     * Checks if the required runtime permissions for Wi-Fi scanning are granted.
     */
    fun checkPermissions(): List<String> {
        val missing = mutableListOf<String>()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            missing.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearby = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNearby) {
                missing.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        return missing
    }

    /**
     * Inspects the currently connected Wi-Fi network and queries available scan results.
     */
    fun scanNetworks(forceFresh: Boolean = false): WifiScanResult {
        if (wifiManager == null) {
            return WifiScanResult.Error("Wi-Fi hardware manager is unavailable on this device.")
        }

        if (!wifiManager.isWifiEnabled) {
            return WifiScanResult.Error("Wi-Fi is currently turned off in device settings.")
        }

        val missingPerms = checkPermissions()
        val connectedNetwork = getConnectedNetworkInfo()

        // If location/nearby permission is missing, return current connection with permission note
        if (missingPerms.isNotEmpty()) {
            return if (connectedNetwork != null) {
                WifiScanResult.Success(
                    connectedNetwork = connectedNetwork,
                    nearbyNetworks = emptyList(),
                    isThrottled = false,
                    message = "Connected Wi-Fi information gathered. Note: Grant Location/Nearby Wi-Fi permissions to scan surrounding access points."
                )
            } else {
                WifiScanResult.PermissionRequired(
                    missingPermissions = missingPerms,
                    explanation = "Scanning nearby Wi-Fi networks requires Location/Nearby Wi-Fi permission as mandated by Android."
                )
            }
        }

        val now = System.currentTimeMillis()
        if (!forceFresh && cachedScanResult != null && (now - lastScanTimestamp) < SCAN_COOLDOWN_MS) {
            return cachedScanResult!!.copy(
                connectedNetwork = connectedNetwork,
                isThrottled = true,
                message = "Displaying recent Wi-Fi scan results (cooldown active to avoid Android system scan throttling)."
            )
        }

        return try {
            val scanResults = try {
                wifiManager.scanResults ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }

            val nearby = scanResults.map { scan ->
                analyzer.buildNetworkInfo(
                    ssid = scan.SSID ?: "<Hidden Network>",
                    bssid = scan.BSSID ?: "00:00:00:00:00:00",
                    rssi = scan.level,
                    frequencyMhz = scan.frequency,
                    capabilities = scan.capabilities ?: "",
                    isConnected = connectedNetwork?.bssid.equals(scan.BSSID, ignoreCase = true)
                )
            }.sortedByDescending { it.rssi }

            lastScanTimestamp = now
            val result = WifiScanResult.Success(
                connectedNetwork = connectedNetwork,
                nearbyNetworks = nearby,
                isThrottled = false,
                message = "Discovered ${nearby.size} access points. Connected network analyzed."
            )
            cachedScanResult = result
            result
        } catch (e: Exception) {
            WifiScanResult.Error("Failed to collect Wi-Fi scan results: ${e.message}")
        }
    }

    /**
     * Gathers network details for the currently connected Wi-Fi network.
     */
    fun getConnectedNetworkInfo(): WifiNetworkInfo? {
        val manager = wifiManager ?: return null
        val info = manager.connectionInfo ?: return null
        if (info.networkId == -1 || info.bssid == null) return null

        var ipAddress: String? = null
        var gateway: String? = null
        val dnsServers = mutableListOf<String>()

        connectivityManager?.let { cm ->
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val linkProps = cm.getLinkProperties(activeNetwork)
                linkProps?.linkAddresses?.forEach { addr ->
                    if (addr.address is Inet4Address) {
                        ipAddress = addr.address.hostAddress
                    }
                }
                linkProps?.routes?.forEach { route ->
                    if (route.isDefaultRoute && route.gateway is Inet4Address) {
                        gateway = route.gateway?.hostAddress
                    }
                }
                linkProps?.dnsServers?.forEach { dns ->
                    if (dns is Inet4Address) {
                        dnsServers.add(dns.hostAddress ?: "")
                    }
                }
            }
        }

        return analyzer.buildNetworkInfo(
            ssid = info.ssid ?: "Connected Wi-Fi",
            bssid = info.bssid ?: "00:00:00:00:00:00",
            rssi = info.rssi,
            frequencyMhz = info.frequency,
            capabilities = "[WPA2-PSK-CCMP]", // default connected baseline
            isConnected = true,
            ipAddress = ipAddress,
            gateway = gateway,
            dnsServers = dnsServers.filter { it.isNotBlank() }
        )
    }
}
