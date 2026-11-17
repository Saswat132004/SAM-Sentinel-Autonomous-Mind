package com.sam.assistant.automation.device

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import com.sam.assistant.automation.executor.ExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryStatusReport(
    val percentage: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val healthStatus: String
)

class DeviceController(private val context: Context) {

    companion object {
        private const val TAG = "DeviceController"
    }

    private var isTorchOn = false

    fun toggleFlashlight(turnOn: Boolean? = null): ExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ExecutionResult.Error("No camera with flash unit available on this device.")

            val newState = turnOn ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, newState)
            isTorchOn = newState
            val stateText = if (newState) "on" else "off"
            ExecutionResult.Success(
                message = "Flashlight turned $stateText.",
                spokenResponse = "Flashlight turned $stateText."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            ExecutionResult.Error("Unable to toggle flashlight: ${e.message}")
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    fun readBatteryLevel(): ExecutionResult {
        val report = readBatteryDetailed()
        if (report != null) {
            val chargeText = if (report.isCharging) "and currently charging" else "and not charging"
            val text = "Battery is at ${report.percentage} percent $chargeText."
            return ExecutionResult.Success(
                message = "$text (Temp: ${report.temperatureCelsius}°C, Health: ${report.healthStatus})",
                spokenResponse = text
            )
        }
        return ExecutionResult.Error("Could not determine battery level.")
    }

    fun readBatteryDetailed(): BatteryStatusReport? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

        if (level >= 0 && scale > 0) {
            val percent = ((level.toFloat() / scale.toFloat()) * 100).toInt()
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val tempC = tempTenths / 10.0f
            val healthText = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Normal"
            }
            return BatteryStatusReport(percent, isCharging, tempC, healthText)
        }
        return null
    }

    fun readDateTime(): ExecutionResult {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        return ExecutionResult.Success(
            message = "The time is $timeStr on $dateStr.",
            spokenResponse = "The time is $timeStr on $dateStr."
        )
    }

    fun adjustVolume(direction: String): ExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adjustDirection = when (direction.lowercase()) {
            "up", "raise", "increase" -> AudioManager.ADJUST_RAISE
            "down", "lower", "decrease" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> AudioManager.ADJUST_SAME
        }
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            adjustDirection,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct = ((current.toFloat() / max.toFloat()) * 100).toInt()
        return ExecutionResult.Success(
            message = "Media volume set to $pct% ($current/$max).",
            spokenResponse = "Volume adjusted."
        )
    }

    fun setSystemAlarm(hour: Int, minute: Int, message: String = "Alarm", skipUi: Boolean = true): ExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeFormatted = String.format("%02d:%02d", hour, minute)
            ExecutionResult.Success(
                message = "Alarm set for $timeFormatted.",
                spokenResponse = "Alarm set for $timeFormatted."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm", e)
            ExecutionResult.Error("Unable to set alarm: ${e.message}")
        }
    }

    fun setSystemTimer(lengthSeconds: Int, message: String = "Timer", skipUi: Boolean = true): ExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val minutes = lengthSeconds / 60
            ExecutionResult.Success(
                message = "Timer set for $minutes minutes.",
                spokenResponse = "Timer set for $minutes minutes."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            ExecutionResult.Error("Unable to set timer: ${e.message}")
        }
    }

    fun openSystemSettings(settingType: String): ExecutionResult {
        val intent = when (settingType.lowercase()) {
            "wifi", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound", "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "date", "time" -> Intent(Settings.ACTION_DATE_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ExecutionResult.Success(
                message = "Opening $settingType settings.",
                spokenResponse = "Opening $settingType settings."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings: $settingType", e)
            ExecutionResult.Error("Could not open settings for $settingType.")
        }
    }

    fun openApplicationSettings(packageName: String): ExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opening app settings for $packageName.")
        } catch (e: Exception) {
            ExecutionResult.Error("Unable to open app settings.")
        }
    }
}
