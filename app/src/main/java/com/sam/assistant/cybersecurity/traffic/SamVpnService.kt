package com.sam.assistant.cybersecurity.traffic

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SamVpnService : VpnService() {

    companion object {
        private const val TAG = "SamVpnService"
        const val ACTION_START = "com.sam.assistant.VPN_START"
        const val ACTION_STOP = "com.sam.assistant.VPN_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        val liveBytesRx = AtomicLong(0)
        val liveBytesTx = AtomicLong(0)
        val livePacketCount = AtomicLong(0)
        val liveProtocols = ConcurrentHashMap<String, Long>()
        val liveDestinations = ConcurrentHashMap<String, Long>()

        fun resetMetrics() {
            liveBytesRx.set(0)
            liveBytesTx.set(0)
            livePacketCount.set(0)
            liveProtocols.clear()
            liveDestinations.clear()
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val isStopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        Log.d(TAG, "Starting privacy-preserving local VPN monitoring session...")
        isStopping.set(false)
        resetMetrics()

        try {
            val builder = Builder()
                .setSession("SAM Security Monitor")
                .addAddress("10.1.0.2", 32)
                .addRoute("10.1.0.0", 24)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .setBlocking(false)

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                isRunning = true
                startTrafficWorker(vpnInterface!!)
                Log.d(TAG, "Local VPN interface established successfully.")
            } else {
                Log.e(TAG, "Failed to establish VPN interface (null descriptor).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN startup exception", e)
            isRunning = false
        }
    }

    private fun startTrafficWorker(pfd: ParcelFileDescriptor) {
        vpnJob = scope.launch {
            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(pfd.fileDescriptor)

            try {
                while (isActive && !isStopping.get()) {
                    val read = inputStream.channel.read(buffer)
                    if (read > 0) {
                        buffer.flip()
                        processPacketMetadata(buffer, read)
                        buffer.clear()
                    } else {
                        delay(20) // Yield to avoid busy spinning
                    }
                }
            } catch (e: Exception) {
                if (!isStopping.get()) {
                    Log.w(TAG, "VPN monitoring stream finished: ${e.message}")
                }
            }
        }
    }

    /**
     * Statically inspects IP packet header metadata without logging packet payloads.
     */
    private fun processPacketMetadata(packet: ByteBuffer, length: Int) {
        livePacketCount.incrementAndGet()
        liveBytesRx.addAndGet(length.toLong())

        if (length < 20) return // Below minimum IPv4 header length

        val versionAndIhl = packet.get(0).toInt()
        val version = (versionAndIhl shr 4) and 0x0F
        if (version != 4) return // IPv4 evaluation

        val protocolByte = packet.get(9).toInt() and 0xFF
        val protocolName = when (protocolByte) {
            6 -> "TCP"
            17 -> "UDP"
            1 -> "ICMP"
            else -> "Protocol-$protocolByte"
        }
        liveProtocols.merge(protocolName, 1L) { old, new -> old + new }

        // Destination IP extraction (bytes 16..19)
        val d1 = packet.get(16).toInt() and 0xFF
        val d2 = packet.get(17).toInt() and 0xFF
        val d3 = packet.get(18).toInt() and 0xFF
        val d4 = packet.get(19).toInt() and 0xFF
        val destIp = "$d1.$d2.$d3.$d4"

        liveDestinations.merge(destIp, 1L) { old, new -> old + new }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping local VPN monitoring...")
        isStopping.set(true)
        isRunning = false
        vpnJob?.cancel()

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN descriptor", e)
        }

        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
