package com.sam.assistant.core.security

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LockStateManager {
    private const val TAG = "LockStateManager"

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isScreenInteractive = MutableStateFlow(true)
    val isScreenInteractive: StateFlow<Boolean> = _isScreenInteractive.asStateFlow()

    private val lockStateListeners = mutableListOf<(Boolean) -> Unit>()

    fun registerListener(listener: (isLocked: Boolean) -> Unit) {
        synchronized(lockStateListeners) {
            lockStateListeners.add(listener)
            listener(_isLocked.value)
        }
    }

    fun unregisterListener(listener: (isLocked: Boolean) -> Unit) {
        synchronized(lockStateListeners) {
            lockStateListeners.remove(listener)
        }
    }

    fun checkDeviceLockState(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isDeviceLocked = keyguardManager?.isDeviceLocked ?: false
        val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
        val isInteractive = powerManager?.isInteractive ?: true

        val locked = isDeviceLocked || isKeyguardLocked || !isInteractive
        
        _isScreenInteractive.value = isInteractive
        val stateChanged = _isLocked.value != locked
        _isLocked.value = locked

        if (stateChanged) {
            Log.d(TAG, "Device lock state changed -> locked: $locked (keyguard: $isKeyguardLocked, deviceLocked: $isDeviceLocked, interactive: $isInteractive)")
            notifyListeners(locked)
        }

        return locked
    }

    fun setLockedState(locked: Boolean) {
        val stateChanged = _isLocked.value != locked
        _isLocked.value = locked
        if (stateChanged) {
            Log.d(TAG, "Explicit lock state update -> locked: $locked")
            notifyListeners(locked)
        }
    }

    fun setInteractive(interactive: Boolean) {
        _isScreenInteractive.value = interactive
        if (!interactive) {
            // When screen turns off, lock state is immediately considered locked
            setLockedState(true)
        }
    }

    private fun notifyListeners(locked: Boolean) {
        val listenersCopy = synchronized(lockStateListeners) { lockStateListeners.toList() }
        for (listener in listenersCopy) {
            try {
                listener(locked)
            } catch (e: Exception) {
                Log.e(TAG, "Error in lock state listener", e)
            }
        }
    }

    /**
     * Security barrier: Throws SecurityException if called while device is locked.
     */
    fun assertUnlocked() {
        if (_isLocked.value) {
            throw SecurityException("Lock-state security violation: Attempted personal data or automation operation while phone is locked.")
        }
    }
}

