package com.distrigo.app.data.print.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Classic Bluetooth RFCOMM, which is what thermal receipt printers speak.
 *
 * Not BLE. Every 58/80 mm printer in this price range exposes the **Serial Port Profile**, whose
 * well-known UUID is below, and treats it as a byte pipe with no protocol of its own: whatever is
 * written is interpreted as ESC/POS (or TSPL, or CPCL) commands. There is no read direction worth
 * relying on, which is why paper width is chosen rather than queried.
 */
class BluetoothSppTransport(private val context: Context) : PrinterTransport {

    override suspend fun send(address: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val device = resolveDevice(address)
        var socket: BluetoothSocket? = null
        try {
            socket = connect(device)
            val out = socket.outputStream
            // Written in chunks with a breath between them. A cheap printer's receive buffer is a few
            // hundred bytes and it does not apply back-pressure: hand it a whole receipt at once and
            // the overflow is silent — the paper comes out with a band of the middle missing. Nothing
            // in the API reports this, so the pacing is the fix.
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK_BYTES, bytes.size)
                out.write(bytes, offset, end - offset)
                out.flush()
                offset = end
                if (offset < bytes.size) Thread.sleep(CHUNK_PAUSE_MS)
            }
            // The head is still burning when the last byte lands; closing immediately truncates the
            // tail on some models.
            Thread.sleep(DRAIN_MS)
        } catch (e: IOException) {
            // A break after the socket opened means the paper already holds part of the receipt, which
            // the caller has to tell the user about differently from "it never started".
            throw PrintException(if (socket?.isConnected == true) PrintFailure.INTERRUPTED else PrintFailure.UNREACHABLE, e)
        } catch (e: SecurityException) {
            throw PrintException(PrintFailure.PERMISSION_DENIED, e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    override suspend fun isReachable(address: String): Boolean = withContext(Dispatchers.IO) {
        val device = runCatching { resolveDevice(address) }.getOrNull() ?: return@withContext false
        var socket: BluetoothSocket? = null
        try {
            socket = connect(device)
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    @SuppressLint("MissingPermission") // Checked by requireConnectPermission below, which throws first.
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        requireConnectPermission()
        // Discovery and a connection attempt fight over the same radio, and discovery wins — leaving
        // the connect to time out for no visible reason. Cancelling first is the documented remedy.
        runCatching { adapter()?.cancelDiscovery() }

        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        return socket
    }

    @SuppressLint("MissingPermission")
    private fun resolveDevice(address: String): BluetoothDevice {
        val adapter = adapter() ?: throw PrintException(PrintFailure.NO_BLUETOOTH)
        if (!adapter.isEnabled) throw PrintException(PrintFailure.ADAPTER_OFF)
        requireConnectPermission()
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
            ?: throw PrintException(PrintFailure.NOT_PAIRED)
        // A pairing removed in Android's own settings leaves the address saved here and nothing to
        // connect to. Saying "not paired" sends the user somewhere useful; letting the connect fail
        // would say "unreachable" and send them looking for the printer.
        if (device.bondState != BluetoothDevice.BOND_BONDED) throw PrintException(PrintFailure.NOT_PAIRED)
        return device
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private fun requireConnectPermission() {
        if (!hasConnectPermission(context)) throw PrintException(PrintFailure.PERMISSION_DENIED)
    }

    companion object {
        /** The Serial Port Profile's well-known UUID. Every SPP printer answers on it. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val CHUNK_BYTES = 256
        private const val CHUNK_PAUSE_MS = 20L
        private const val DRAIN_MS = 250L

        /**
         * BLUETOOTH_CONNECT exists from API 31. Below that, connecting is covered by the install-time
         * BLUETOOTH permission, so there is nothing to ask for and this is always true.
         */
        fun hasConnectPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Scanning needs BLUETOOTH_SCAN from API 31, and ACCESS_FINE_LOCATION below it — classic
         * discovery was a location-adjacent capability before the dedicated permission existed.
         */
        fun hasScanPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            }

        /** What to ask for, in the order the system expects them, for the API this phone is on. */
        fun connectPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            else emptyArray()

        fun scanPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
