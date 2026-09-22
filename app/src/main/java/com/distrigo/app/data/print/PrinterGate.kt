package com.distrigo.app.data.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.distrigo.app.data.print.transport.BluetoothSppTransport
import com.distrigo.app.data.print.transport.PrintFailure

/**
 * Where the printer stands right now.
 *
 * One type for every transport, so a screen has a single `when` rather than one branch per radio, and
 * so the settings screen and the print button cannot disagree about whether printing would work.
 */
sealed interface PrinterLink {

    /** Nothing has been chosen to print to. */
    data object NotConfigured : PrinterLink

    /** Chosen, paired, radio on. Nothing has been opened — a print would be expected to succeed. */
    data class Idle(val printer: SavedPrinter) : PrinterLink

    /** A job is opening the socket. */
    data class Connecting(val printer: SavedPrinter) : PrinterLink

    /** Answered a reachability probe. */
    data class Ready(val printer: SavedPrinter) : PrinterLink

    /**
     * Cannot print, and why.
     *
     * [printer] is null when nothing is chosen yet but something is already wrong — the radio off, or
     * the permission refused — which is worth saying before the user goes looking for a printer that
     * could not have been found anyway.
     */
    data class Blocked(val printer: SavedPrinter?, val reason: PrintFailure) : PrinterLink
}

/**
 * The pre-flight check, run in one place.
 *
 * Every path to the printer goes through here in the same order — radio present, permission granted,
 * radio on, still paired — so the settings screen cannot show "Prête" while the print button fails on
 * a pairing that was removed in Android's own settings an hour ago.
 *
 * [check] does no I/O and opens no socket: it reads adapter state and the bond list, which is cheap
 * enough to call on every recomposition. Proving the printer actually answers costs a connection, so
 * that is [probe]'s job and is only worth doing when the user asks.
 */
class PrinterGate(private val context: Context) {

    private val transport = BluetoothSppTransport(context)

    fun check(printer: SavedPrinter?): PrinterLink {
        val adapter = adapter()
            ?: return PrinterLink.Blocked(printer, PrintFailure.NO_BLUETOOTH)

        if (!BluetoothSppTransport.hasConnectPermission(context))
            return PrinterLink.Blocked(printer, PrintFailure.PERMISSION_DENIED)

        if (!adapter.isEnabled)
            return PrinterLink.Blocked(printer, PrintFailure.ADAPTER_OFF)

        if (printer == null) return PrinterLink.NotConfigured

        return if (isBonded(adapter, printer.id)) PrinterLink.Idle(printer)
        else PrinterLink.Blocked(printer, PrintFailure.NOT_PAIRED)
    }

    /**
     * [check], then an actual connection to prove the printer is in range and free.
     *
     * A printer that is paired, powered and already talking to another phone passes every check above
     * and still refuses the socket, which is the failure a user is least likely to guess at.
     */
    suspend fun probe(printer: SavedPrinter?): PrinterLink {
        val checked = check(printer)
        if (checked !is PrinterLink.Idle) return checked
        return if (transport.isReachable(checked.printer.id)) PrinterLink.Ready(checked.printer)
        else PrinterLink.Blocked(checked.printer, PrintFailure.UNREACHABLE)
    }

    private fun isBonded(adapter: BluetoothAdapter, address: String): Boolean = try {
        adapter.bondedDevices.orEmpty().any { it.address.equalsIgnoreCase(address) }
    } catch (e: SecurityException) {
        // The permission was revoked between the check above and this read.
        false
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)
}

/** What a [PrintFailure] means to someone holding the phone, and what they can do about it. */
data class PrinterProblemMessage(val title: String, val detail: String, val action: String?)

/**
 * The French wording for every failure, in one place.
 *
 * Kept out of the composables because the same failure is reported from the settings screen, from the
 * printer list and — in phase 3 — from the print button on a receipt, and three hand-written versions
 * of "l'imprimante ne répond pas" would eventually disagree about what the user should do next.
 */
fun PrintFailure.message(): PrinterProblemMessage = when (this) {
    PrintFailure.NO_BLUETOOTH -> PrinterProblemMessage(
        "Bluetooth indisponible",
        "Ce téléphone n'a pas de Bluetooth. Utilisez le partage en PDF.",
        null,
    )
    PrintFailure.PERMISSION_DENIED -> PrinterProblemMessage(
        "Autorisation refusée",
        "DistriGo a besoin d'accéder au Bluetooth pour parler à l'imprimante.",
        "Autoriser",
    )
    PrintFailure.ADAPTER_OFF -> PrinterProblemMessage(
        "Bluetooth désactivé",
        "Activez le Bluetooth pour utiliser l'imprimante.",
        "Activer",
    )
    PrintFailure.NOT_PAIRED -> PrinterProblemMessage(
        "Imprimante non jumelée",
        "Jumelez l'imprimante dans les réglages Bluetooth du téléphone, puis réessayez.",
        "Ouvrir les réglages",
    )
    PrintFailure.UNREACHABLE -> PrinterProblemMessage(
        "Imprimante injoignable",
        "Vérifiez qu'elle est allumée, à portée, et qu'aucun autre téléphone n'y est connecté.",
        "Réessayer",
    )
    PrintFailure.INTERRUPTED -> PrinterProblemMessage(
        "Impression interrompue",
        "La connexion a été perdue pendant l'impression. Le reçu est incomplet.",
        "Réimprimer",
    )
    PrintFailure.NOT_CONFIGURED -> PrinterProblemMessage(
        "Aucune imprimante",
        "Choisissez une imprimante pour pouvoir imprimer.",
        "Choisir",
    )
}

/** The bonded devices, as the selection screen lists them. Empty if the permission is not granted. */
fun bondedDevices(context: Context): List<BluetoothDevice> = try {
    if (!BluetoothSppTransport.hasConnectPermission(context)) emptyList()
    else (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
        ?.adapter?.bondedDevices.orEmpty().toList()
} catch (e: SecurityException) {
    emptyList()
}
