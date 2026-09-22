package com.distrigo.app.data.print.discovery

import android.content.Context
import com.distrigo.app.data.print.transport.NetworkAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Finds network printers by knocking on port 9100 across the local /24.
 *
 * Crude, and deliberately so. The polite alternative is mDNS — `_pdl-datastream._tcp` and friends —
 * but the till printers this is for are cheap network modules that answer on 9100 and advertise
 * nothing at all. A sweep finds those; mDNS finds the office machines that should be going through
 * Android's own print service anyway.
 *
 * Typing an IP by hand stays the reliable path, because a printer prints its own address on its
 * self-test page. This is the convenience for everyone who does not want to.
 */
class NetworkPrinterScanner(private val context: Context) {

    private val wifi = WifiInfoProvider(context)

    /** Whether there is a local IPv4 subnet to sweep at all. */
    fun canScan(): Boolean = wifi.localSubnetPrefix() != null

    /**
     * Emits every host on this phone's /24 that accepts a connection on [port].
     *
     * 254 addresses, [PARALLELISM] at a time, each given [PROBE_TIMEOUT_MS]. Bounded because a phone
     * that opens 254 sockets at once mostly discovers its own file-descriptor limit, and because the
     * radio has better things to do; the whole sweep still finishes in a couple of seconds.
     *
     * The collector cancelling cancels every outstanding connect, so leaving the screen stops it.
     */
    fun scan(port: Int = NetworkAddress.DEFAULT_PORT): Flow<DiscoveredDevice> = callbackFlow {
        val prefix = wifi.localSubnetPrefix()
        if (prefix == null) { close(); return@callbackFlow }

        val gate = Semaphore(PARALLELISM)
        val sweep = launch {
            val probes = (1..254).map { host ->
                launch {
                    gate.withPermit {
                        val ip = "$prefix.$host"
                        if (accepts(ip, port)) {
                            trySend(
                                DiscoveredDevice(
                                    name             = ip,
                                    address          = NetworkAddress(ip, port).toString(),
                                    bonded           = false,
                                    // Anything answering on 9100 is a print server by convention;
                                    // nothing else has a reason to be listening there.
                                    looksLikePrinter = true,
                                )
                            )
                        }
                    }
                }
            }
            probes.forEach { it.join() }
            close()
        }

        awaitClose { sweep.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun accepts(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            true
        }
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val PARALLELISM = 48
        const val PROBE_TIMEOUT_MS = 400
    }
}
