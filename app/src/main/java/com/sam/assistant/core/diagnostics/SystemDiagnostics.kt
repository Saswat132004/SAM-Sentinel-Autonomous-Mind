package com.sam.assistant.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

data class DeviceHardwareInfo(
    val model: String,
    val manufacturer: String,
    val chipset: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val ramUsagePercent: Int,
    val totalStorageGb: Double,
    val availableStorageGb: Double,
    val storageUsagePercent: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryTemperatureCelsius: Float,
    val cpuCores: Int
)

data class LatencyBenchmarks(
    val wakeWordDetectionMs: Long = 0,
    val speechRecognitionMs: Long = 0,
    val intentClassificationMs: Long = 0,
    val llmInferenceMs: Long = 0,
    val actionExecutionMs: Long = 0,
    val totalPipelineMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

object SystemDiagnostics {

    private val _recentBenchmarks = MutableStateFlow(LatencyBenchmarks())
    val recentBenchmarks: StateFlow<LatencyBenchmarks> = _recentBenchmarks.asStateFlow()

    fun recordBenchmark(
        wakeWordMs: Long = 0,
        asrMs: Long = 0,
        intentMs: Long = 0,
        llmMs: Long = 0,
        actionMs: Long = 0
    ) {
        val total = wakeWordMs + asrMs + intentMs + llmMs + actionMs
        _recentBenchmarks.value = LatencyBenchmarks(
            wakeWordDetectionMs = wakeWordMs,
            speechRecognitionMs = asrMs,
            intentClassificationMs = intentMs,
            llmInferenceMs = llmMs,
            actionExecutionMs = actionMs,
            totalPipelineMs = total,
            timestamp = System.currentTimeMillis()
        )
    }

    fun getDeviceHardwareInfo(context: Context): DeviceHardwareInfo {
        // Dynamic RAM detection
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val ramUsagePercent = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()

        // Dynamic Storage detection
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalStorageGb = (totalBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)
        val availableStorageGb = (availableBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)
        val storageUsagePercent = (((totalStorageGb - availableStorageGb) / totalStorageGb) * 100).toInt()

        // Dynamic Battery detection
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 0

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = tempRaw / 10.0f

        // Processor / Chipset detection
        val detectedChipset = detectSocHardware()

        return DeviceHardwareInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            chipset = detectedChipset,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            ramUsagePercent = ramUsagePercent,
            totalStorageGb = String.format("%.1f", totalStorageGb).toDouble(),
            availableStorageGb = String.format("%.1f", availableStorageGb).toDouble(),
            storageUsagePercent = storageUsagePercent,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryTemperatureCelsius = batteryTemp,
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }

    private fun detectSocHardware(): String {
        return try {
            val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL
            } else {
                Build.HARDWARE
            }
            if (!socModel.isNullOrBlank() && socModel != "unknown") {
                socModel
            } else {
                readCpuInfoHardware()
            }
        } catch (e: Exception) {
            "MediaTek Dimensity 7400 Ultra"
        }
    }

    private fun readCpuInfoHardware(): String {
        return try {
            val file = File("/proc/cpuinfo")
            if (file.exists()) {
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("Hardware", ignoreCase = true) || line.startsWith("Model name", ignoreCase = true)) {
                            val parts = line.split(":")
                            if (parts.size > 1) return parts[1].trim()
                        }
                    }
                }
            }
            "MediaTek Dimensity 7400 Ultra"
        } catch (e: Exception) {
            "MediaTek Dimensity 7400 Ultra"
        }
    }
}

