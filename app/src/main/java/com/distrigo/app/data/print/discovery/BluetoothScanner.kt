package com.distrigo.app.data.print.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.distrigo.app.data.print.transport.BluetoothSppTransport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A device that could be a printer.
 *
 * [looksLikePrinter] is a hint for ordering, never a filter. The Bluetooth class of a genuine printer
 * is IMAGING/PRINTER, but plenty of inexpensive units report UNCATEGORIZED — hiding those would hide
 * exactly the printers our users buy. So everything is listed, and the likely ones float to the top.
 */
data class DiscoveredDevice(
    val name           : String,
    val address        : String,
    val bonded         : Boolean,
    val looksLikePrinter: Boolean,
)

/**
 * Finding printers, bonded ones first.
 *
 * [bonded] is the path that matters. Most users pair a printer once in Android's own Bluetooth
 * settings, and reading the bond list needs only BLUETOOTH_CONNECT — no scan, no location, no
 * permission dialog on most phones. Discovery is the fallback for the minority who have not paired,
 * and it is the expensive one: classic inquiry saturates the radio for its whole duration.
 */
class BluetoothScanner(private val context: Context) {

    /** Whether this phone has a Bluetooth radio at all. */
    fun isSupported(): Boolean = adapter() != null

    fun isEnabled(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission.
    fun bonded(): List<DiscoveredDevice> = try {
        if (!BluetoothSppTransport.hasConnectPermission(context)) emptyList()
        else adapter()?.bondedDevices.orEmpty().map { it.toDiscovered(bonded = true) }
            .sortedByDescending { it.looksLikePrinter }
    } catch (e: SecurityException) {
        emptyList()
    }

    /**
     * Devices found by inquiry, emitted as they appear.
     *
     * The flow completes when Android reports discovery finished — about 12 seconds — and cancels the
     * inquiry if the collector goes away first, so leaving the screen does not leave the radio
     * scanning. The caller is expected to impose its own ceiling as well; see PrinterSelectionViewModel.
     */
    @SuppressLint("MissingPermission") // Guarded by hasScanPermission; the flow closes if it is absent.
    fun discover(): Flow<DiscoveredDevice> = callbackFlow {
        val adapter = adapter()
        if (adapter == null || !adapter.isEnabled || !BluetoothSppTransport.hasScanPermission(context)) {
            close(); return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else
                                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let { runCatching { trySend(it.toDiscovered(bonded = false)) } }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }

        ContextCompat_registerReceiver(receiver)

        try {
            adapter.cancelDiscovery()
            if (!adapter.startDiscovery()) close()
        } catch (e: SecurityException) {
            close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { adapter.cancelDiscovery() }
        }
    }

    /** Stops an inquiry started elsewhere — the cancel button, and leaving the screen. */
    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        runCatching { adapter()?.cancelDiscovery() }
    }

    private fun ContextCompat_registerReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // API 34 requires every dynamically registered receiver to declare its exposure. These are
        // system broadcasts, so NOT_EXPORTED is both correct and the stricter choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDiscovered(bonded: Boolean): DiscoveredDevice {
        val readableName = runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address
        val major = runCatching { bluetoothClass?.majorDeviceClass }.getOrNull()
        val exact = runCatching { bluetoothClass?.deviceClass }.getOrNull()
        return DiscoveredDevice(
            name    = readableName,
            address = address,
            bonded  = bonded,
            looksLikePrinter =
                major == BluetoothClass.Device.Major.IMAGING ||
                exact == BluetoothClass.Device.Major.IMAGING ||
                // The name is the only signal an uncategorised clone gives, and these are the words
                // they ship with.
                PRINTER_NAME_HINTS.any { readableName.contains(it, ignoreCase = true) },
        )
    }

    private companion object {
        val PRINTER_NAME_HINTS = listOf("print", "pos", "thermal", "rpp", "mtp", "mpt", "ticket", "receipt")
    }
}
