package com.sam.assistant.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LockStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "LockStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "ACTION_SCREEN_OFF received: locking SAM and cutting microphone/voice immediately")
                LockStateManager.setInteractive(false)
                LockStateManager.setLockedState(true)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "ACTION_SCREEN_ON received: evaluating keyguard state")
                LockStateManager.setInteractive(true)
                LockStateManager.checkDeviceLockState(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "ACTION_USER_PRESENT received: User has successfully unlocked device")
                LockStateManager.setInteractive(true)
                LockStateManager.setLockedState(false)
            }
        }
    }
}

