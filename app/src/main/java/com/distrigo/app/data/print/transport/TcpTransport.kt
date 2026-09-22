package com.distrigo.app.data.print.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A network printer, reached as a raw TCP socket.
 *
 * Almost every Wi-Fi or Ethernet receipt printer listens on **port 9100** and treats the connection
 * as the same dumb byte pipe Bluetooth SPP is: whatever is written is interpreted as ESC/POS. There
 * is no protocol, no handshake and no reply worth waiting for — which is why paper width cannot be
 * queried here either (docs/print_architecture.md §2).
 *
 * The exception would be a printer speaking IPP, which *does* describe its media. Those are
 * AirPrint-class office machines, they are reached through Android's own print service, and a user
 * who has one should pick A4 and let the OS dialog talk to it. This transport is for the till
 * printer on the shop's Wi-Fi.
 */
class TcpTransport : PrinterTransport {

    override val pacing = Pacing(CHUNK_BYTES, CHUNK_PAUSE_MS, DRAIN_MS)

    override suspend fun send(address: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = NetworkAddress.parse(address) ?: throw PrintException(PrintFailure.NOT_CONFIGURED)
        var socket: Socket? = null
        try {
            socket = open(target)
            val out = socket.getOutputStream()
            // Paced like the Bluetooth transport, and for the same reason: the printer's buffer is a
            // few hundred bytes whether the bytes arrived over a radio or a wire, and TCP's own flow
            // control protects the socket, not the print head behind it.
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK_BYTES, bytes.size)
                out.write(bytes, offset, end - offset)
                out.flush()
                offset = end
                if (offset < bytes.size) Thread.sleep(CHUNK_PAUSE_MS)
            }
            Thread.sleep(DRAIN_MS)
        } catch (e: IOException) {
            // Connected and then broken means the paper already holds part of the receipt, which the
            // user has to hear about differently from a job that never started.
            throw PrintException(
                if (socket?.isConnected == true) PrintFailure.INTERRUPTED else PrintFailure.UNREACHABLE,
                e,
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    override suspend fun isReachable(address: String): Boolean = withContext(Dispatchers.IO) {
        val target = NetworkAddress.parse(address) ?: return@withContext false
        try {
            open(target).use { true }
        } catch (e: IOException) {
            false
        }
    }

    private fun open(target: NetworkAddress): Socket = Socket().apply {
        // Nagle batches small writes, which is the opposite of what a print head wants: it holds the
        // first line back waiting for company and the paper stalls mid-receipt.
        tcpNoDelay = true
        connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
    }

    companion object {
        const val CHUNK_BYTES = 1024
        const val CHUNK_PAUSE_MS = 10L
        const val DRAIN_MS = 250L

        /**
         * Short on purpose. A printer on the same Wi-Fi answers in milliseconds; anything that takes
         * seconds is switched off or on another network, and the user is standing at a counter.
         */
        const val CONNECT_TIMEOUT_MS = 2_000
    }
}

/**
 * A network printer's address, stored as `host:port`.
 *
 * The same [SavedPrinter][com.distrigo.app.data.print.SavedPrinter] `id` field holds a MAC address
 * for Bluetooth and this for Wi-Fi — one identity column, parsed by whichever transport owns it.
 */
data class NetworkAddress(val host: String, val port: Int) {

    override fun toString(): String = "$host:$port"

    companion object {
        /** What virtually every network receipt printer listens on. */
        const val DEFAULT_PORT = 9100

        /**
         * An address typed into a form with a separate port box.
         *
         * A port typed into the address itself wins, because an address field gets *pasted* into and
         * what gets pasted is the whole thing — "192.168.1.50:9100" off a printer's self-test page.
         * Appending the port box's value to that would produce host "192.168.1.50:9100" on port 9100,
         * a printer nobody can reach.
         */
        fun resolve(typedHost: String, fallbackPort: Int): NetworkAddress? {
            val parsed = parse(typedHost) ?: return null
            return if (typedHost.contains(':')) parsed else NetworkAddress(parsed.host, fallbackPort)
        }

        /**
         * Parses `host` or `host:port`, or null if there is no host at all.
         *
         * Deliberately permissive about the host: a printer may be addressed by IP or by a name the
         * router hands out, and rejecting anything that is not four dotted numbers would turn a
         * working setup into an error message.
         */
        fun parse(value: String?): NetworkAddress? {
            val text = value?.trim().orEmpty().ifBlank { return null }
            val separator = text.lastIndexOf(':')
            if (separator <= 0) return NetworkAddress(text, DEFAULT_PORT)
            val port = text.substring(separator + 1).toIntOrNull()
            return if (port == null || port !in 1..65535) NetworkAddress(text, DEFAULT_PORT)
            else NetworkAddress(text.substring(0, separator), port)
        }
    }
}
