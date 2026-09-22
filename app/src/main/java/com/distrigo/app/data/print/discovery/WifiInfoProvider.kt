package com.distrigo.app.data.print.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address

/**
 * What network this phone is on, as far as it is allowed to say.
 *
 * The distinction between [Unnamed] and the rest is the point: Android will happily tell an app that
 * Wi-Fi is connected while refusing to name it, and an app that renders that as an empty string looks
 * broken. Each case here has something different to tell the user.
 */
sealed interface WifiState {
    /** No Wi-Fi radio, or it is switched off. */
    data object Off : WifiState

    /** Radio on, but the phone is not on a Wi-Fi network — mobile data, or nothing. */
    data object NotConnected : WifiState

    /** Connected, and this is the network. */
    data class Connected(val ssid: String) : WifiState

    /**
     * Connected, but Android will not name the network.
     *
     * Not a bug and not worth retrying: since API 27 the SSID is gated behind the location permission
     * *and* location services actually being switched on, because knowing which Wi-Fi you are on is
     * knowing roughly where you are. Printing works regardless — the name is reassurance, not a
     * requirement — so this says why rather than demanding the permission.
     */
    data class Unnamed(val reason: Reason) : WifiState {
        enum class Reason {
            /** ACCESS_FINE_LOCATION not granted. */
            PERMISSION,

            /** Granted, but location services are switched off at the system level. */
            LOCATION_OFF,

            /**
             * Everything is granted and on, and Android still will not say.
             *
             * A real case, not a catch-all: from API 31 the WifiInfo carried on a network's
             * capabilities is redacted for anyone who did not ask for location info when registering
             * the callback, and a hidden network has no SSID to report either. Neither is something
             * the user can fix, so this says nothing more than that the name is unavailable — the
             * alternative was claiming the location was off, which on a phone with location on is
             * simply false.
             */
            UNAVAILABLE,
        }
    }
}

/**
 * Reads the current Wi-Fi network, and the local subnet a printer scan should sweep.
 *
 * Nothing here is required to print: a network printer is reached by address, and the address works
 * whether or not the app can name the network it is on. This exists so the printer screen can say
 * "Réseau : DEPOT_WIFI" and make an unreachable printer's cause obvious — usually that the phone
 * has drifted onto mobile data or onto the neighbour's network.
 */
class WifiInfoProvider(private val context: Context) {

    /** The SSID with its quotes stripped, or null for every way Android has of not naming it. */
    private fun readableSsid(info: WifiInfo): String? = info.ssid
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }

    fun state(): WifiState {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return WifiState.Off
        if (!wifiManager.isWifiEnabled) return WifiState.Off

        val info = currentWifiInfo() ?: return WifiState.NotConnected
        val ssid = readableSsid(info)

        return when {
            ssid != null -> WifiState.Connected(ssid)
            !hasLocationPermission() -> WifiState.Unnamed(WifiState.Unnamed.Reason.PERMISSION)
            !isLocationEnabled() -> WifiState.Unnamed(WifiState.Unnamed.Reason.LOCATION_OFF)
            else -> WifiState.Unnamed(WifiState.Unnamed.Reason.UNAVAILABLE)
        }
    }

    /**
     * The `a.b.c` of this phone's IPv4 address on the local network, for a /24 sweep.
     *
     * Read from the active network's link properties rather than `WifiInfo.getIpAddress`, which is
     * deprecated, returns a little-endian int, and is one of the APIs that quietly started returning
     * zero. Null when there is no IPv4 address to sweep from.
     */
    fun localSubnetPrefix(): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = connectivity.activeNetwork ?: return null
        val link: LinkProperties = connectivity.getLinkProperties(network) ?: return null
        val address = link.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress } ?: return null
        return address.hostAddress?.substringBeforeLast('.')?.takeIf { it.count { c -> c == '.' } == 2 }
    }

    @Suppress("DEPRECATION")
    private fun currentWifiInfo(): WifiInfo? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null

        // From API 31 the WifiInfo rides on the network's capabilities — but the copy handed out here
        // is *redacted*: the SSID reads "<unknown ssid>" unless the caller asked for location info
        // when registering a NetworkCallback, which this synchronous read has not. So it is tried
        // first and, when it comes back nameless, the deprecated singleton is asked instead — that
        // one still answers for an app holding the location permission.
        //
        // The clean fix is registerNetworkCallback with setIncludeLocationInfo(true), which is
        // asynchronous and would turn this into a subscription. Worth doing if the SSID ever becomes
        // more than the reassurance it is today.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val fromCapabilities = capabilities.transportInfo as? WifiInfo
            if (fromCapabilities != null && fromCapabilities.hasUsableSsid()) return fromCapabilities
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.connectionInfo
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) capabilities.transportInfo as? WifiInfo else null)
    }

    /** Whether this WifiInfo carries a name we can actually show. */
    private fun WifiInfo.hasUsableSsid(): Boolean = readableSsid(this) != null

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
