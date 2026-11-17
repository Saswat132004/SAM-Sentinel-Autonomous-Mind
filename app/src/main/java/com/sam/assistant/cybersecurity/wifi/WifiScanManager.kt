package com.sam.assistant.cybersecurity.wifi

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiScanManager(val scanner: WifiScanner) {

    constructor(context: Context) : this(WifiScanner(context))

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanResult = MutableStateFlow<WifiScanResult?>(null)
    val lastScanResult: StateFlow<WifiScanResult?> = _lastScanResult.asStateFlow()

    private val _scanResults = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    val scanResults: StateFlow<List<WifiNetworkInfo>> = _scanResults.asStateFlow()

    private val _cooldownSecondsRemaining = MutableStateFlow(0)
    val cooldownSecondsRemaining: StateFlow<Int> = _cooldownSecondsRemaining.asStateFlow()

    fun triggerScan(forceFresh: Boolean = false): WifiScanResult {
        _isScanning.value = true
        val result = scanner.scanNetworks(forceFresh)
        _lastScanResult.value = result
        if (result is WifiScanResult.Success) {
            _scanResults.value = result.nearbyNetworks
        }
        _isScanning.value = false
        return result
    }

    fun startScan(forceFresh: Boolean = true): List<WifiNetworkInfo> {
        val result = triggerScan(forceFresh)
        return if (result is WifiScanResult.Success) {
            result.nearbyNetworks
        } else {
            emptyList()
        }
    }

    fun getConnectedNetwork(): WifiNetworkInfo? {
        return scanner.getConnectedNetworkInfo()
    }

    fun getCurrentConnection(): WifiNetworkInfo? {
        return scanner.getConnectedNetworkInfo()
    }
}
