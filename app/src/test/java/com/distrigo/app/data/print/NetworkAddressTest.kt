package com.distrigo.app.data.print

import com.distrigo.app.data.print.transport.NetworkAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a network printer's address is read back out of the settings file.
 *
 * The same `id` column holds a MAC address for Bluetooth and `host:port` for Wi-Fi, so this parse is
 * the seam between the two — and it runs against whatever a user typed into the dialog, which is
 * where the awkward cases come from.
 */
class NetworkAddressTest {

    @Test
    fun `a bare host takes the default port`() {
        val address = NetworkAddress.parse("192.168.1.50")
        assertEquals("192.168.1.50", address?.host)
        assertEquals(9100, address?.port)
    }

    @Test
    fun `an explicit port is honoured`() {
        val address = NetworkAddress.parse("192.168.1.50:9200")
        assertEquals("192.168.1.50", address?.host)
        assertEquals(9200, address?.port)
    }

    @Test
    fun `surrounding whitespace is forgiven`() {
        // Pasted addresses arrive with spaces more often than not.
        assertEquals(NetworkAddress("10.0.0.7", 9100), NetworkAddress.parse("  10.0.0.7  "))
    }

    @Test
    fun `a nonsense port falls back to the default rather than failing`() {
        // The host is the part that matters, and 9100 is right for virtually every receipt printer.
        // Refusing the whole address over a typo in the port would lose a printer that would work.
        assertEquals(9100, NetworkAddress.parse("192.168.1.50:abc")?.port)
        assertEquals(9100, NetworkAddress.parse("192.168.1.50:0")?.port)
        assertEquals(9100, NetworkAddress.parse("192.168.1.50:70000")?.port)
    }

    @Test
    fun `a hostname is accepted, not just four dotted numbers`() {
        // A printer may be addressed by whatever name the router hands out, and rejecting anything
        // that is not an IP would turn a working setup into an error message.
        assertEquals(NetworkAddress("tm-t20", 9100), NetworkAddress.parse("tm-t20"))
        assertEquals(NetworkAddress("caisse.local", 9100), NetworkAddress.parse("caisse.local"))
    }

    @Test
    fun `nothing at all is nothing`() {
        assertNull(NetworkAddress.parse(null))
        assertNull(NetworkAddress.parse(""))
        assertNull(NetworkAddress.parse("   "))
    }

    @Test
    fun `a port typed into the address beats the one left in the port box`() {
        // The address field is the one that gets pasted into, and a self-test page prints the whole
        // "ip:port". Honouring the box instead would build "192.168.1.50:9100" as a *hostname*.
        assertEquals(NetworkAddress("192.168.1.50", 9100), NetworkAddress.resolve("192.168.1.50:9100", 9100))
        assertEquals(NetworkAddress("192.168.1.50", 80), NetworkAddress.resolve("192.168.1.50:80", 9100))
    }

    @Test
    fun `the port box is used when the address carries no port`() {
        assertEquals(NetworkAddress("192.168.1.50", 9200), NetworkAddress.resolve("192.168.1.50", 9200))
        assertEquals(NetworkAddress("caisse", 9100), NetworkAddress.resolve("caisse", 9100))
    }

    @Test
    fun `resolving nothing is nothing`() {
        assertNull(NetworkAddress.resolve("", 9100))
        assertNull(NetworkAddress.resolve("   ", 9100))
    }

    @Test
    fun `it round trips through the string it is stored as`() {
        val address = NetworkAddress("192.168.1.50", 9100)
        assertEquals(address, NetworkAddress.parse(address.toString()))
    }
}
