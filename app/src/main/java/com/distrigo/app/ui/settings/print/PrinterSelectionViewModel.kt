package com.distrigo.app.ui.settings.print

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.print.PrintResult
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.PrintSettingsStore
import com.distrigo.app.data.print.PrinterGate
import com.distrigo.app.data.print.PrinterLink
import com.distrigo.app.data.print.RasterBenchmark
import com.distrigo.app.data.print.RasterBenchmarkOutcome
import com.distrigo.app.data.print.RasterBenchmarkResult
import com.distrigo.app.data.print.ReceiptPrinter
import com.distrigo.app.data.print.SavedPrinter
import com.distrigo.app.data.print.ConnectionMethod
import com.distrigo.app.data.print.discovery.BluetoothScanner
import com.distrigo.app.data.print.discovery.NetworkPrinterScanner
import com.distrigo.app.data.print.discovery.WifiInfoProvider
import com.distrigo.app.data.print.discovery.WifiState
import com.distrigo.app.data.print.transport.NetworkAddress
import com.distrigo.app.data.print.discovery.DiscoveredDevice
import com.distrigo.app.data.print.transport.PrintFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** What the printer list is doing and showing. */
data class PrinterSelectionState(
    val saved      : List<SavedPrinter>    = emptyList(),
    val selectedId : String?               = null,
    val bonded     : List<DiscoveredDevice> = emptyList(),
    val discovered : List<DiscoveredDevice> = emptyList(),
    val scanning   : Boolean               = false,
    /**
     * Where the printer stands — including every failure.
     *
     * A failed test print or probe lands here rather than in [notice], so there is exactly one place
     * a problem is shown: the banner. It is persistent, it carries its own action, and it does not
     * have to be dismissed before the user can act on it.
     */
    val link       : PrinterLink           = PrinterLink.NotConfigured,
    /**
     * Something that went *right* and leaves nothing on screen to show it — "test envoyé",
     * "l'imprimante répond". Failures never come through here; see [link].
     */
    val notice     : String?               = null,
    val busy       : Boolean               = false,
    /** The printer a connection is being opened to right now, if any. */
    val connectingId: String?              = null,
    /**
     * Why each printer's last connection attempt failed, by address.
     *
     * Per printer rather than one global value: with two saved printers, "non connectée" has to sit on
     * the one that would not answer, not on the list.
     */
    val failures   : Map<String, PrintFailure> = emptyMap(),
    /** Which transport the list is offering to add from. */
    val method     : ConnectionMethod      = ConnectionMethod.DEFAULT,
    /** The network this phone is on, for the Wi-Fi header. Null while Bluetooth is the method. */
    val wifi       : WifiState?            = null,
)

/**
 * The printer list: what is saved, what is paired, and what a scan turned up.
 *
 * Bonded devices are loaded eagerly and scanning is not. Pairing happens once, in Android's own
 * settings, and reading the bond list costs nothing and needs no scan permission — so for most users
 * the printer is already on screen when they arrive and the scan button is never pressed.
 */
@HiltViewModel
class PrinterSelectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PrintSettingsStore,
) : ViewModel() {

    private val scanner    = BluetoothScanner(context)
    private val netScanner = NetworkPrinterScanner(context)
    private val wifi       = WifiInfoProvider(context)
    private val gate       = PrinterGate(context)
    private val printer    = ReceiptPrinter(context, gate)

    private val _state = MutableStateFlow(PrinterSelectionState())
    val state: StateFlow<PrinterSelectionState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            store.flow().collect { settings -> applySettings(settings) }
        }
    }

    /**
     * Re-reads everything that can change behind the screen's back: a permission just granted, a radio
     * just switched on, a network just joined.
     */
    fun refresh() {
        val method = store.current().method
        _state.value = _state.value.copy(
            method = method,
            bonded = if (method == ConnectionMethod.BLUETOOTH)
                scanner.bonded().filterNot { device -> isSaved(device.address) } else emptyList(),
            wifi   = if (method == ConnectionMethod.WIFI) wifi.state() else null,
            link   = gate.check(store.current().selectedPrinter),
        )
    }

    /**
     * Looks for printers on whichever transport is configured.
     *
     * Two very different searches behind one button: a Bluetooth inquiry asks the air what is there,
     * while the network sweep knocks on port 9100 across the local subnet. Both are bounded, both
     * stop when the screen goes away, and both feed the same list.
     */
    fun startScan() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, discovered = emptyList())
        val source = if (_state.value.method == ConnectionMethod.WIFI) netScanner.scan() else scanner.discover()
        scanJob = viewModelScope.launch {
            // Each search ends on its own — Android stops an inquiry after about twelve seconds, and
            // the sweep finishes when its last probe times out — but a radio that never reports
            // finished would leave the button spinning forever. The ceiling is the backstop, not the
            // schedule.
            withTimeoutOrNull(SCAN_CEILING_MS) {
                source
                    .onCompletion { _state.value = _state.value.copy(scanning = false) }
                    .collect { device -> addDiscovered(device) }
            }
            _state.value = _state.value.copy(scanning = false)
        }
    }

    /** Whether a network sweep is possible — there has to be a local subnet to sweep. */
    fun canScanNetwork(): Boolean = netScanner.canScan()

    /**
     * Adds a network printer by address, the way a printer's own self-test page gives it.
     *
     * The reliable path, and the reason the sweep is a convenience rather than the mechanism: a
     * printer on a different subnet, or one behind a router that blocks the sweep, is still reachable
     * by simply being told where it is.
     */
    fun addByAddress(host: String, port: Int) {
        val address = NetworkAddress.resolve(host, port) ?: return
        val defaults = store.current()
        store.addPrinter(
            SavedPrinter(
                id          = address.toString(),
                displayName = address.host,
                method      = ConnectionMethod.WIFI,
                paper       = defaults.defaultPaper,
                language    = defaults.defaultLanguage,
                codePage    = defaults.defaultCodePage,
            )
        )
        select(address.toString())
    }

    fun stopScan() {
        scanJob?.cancel()
        scanner.cancelDiscovery()
        _state.value = _state.value.copy(scanning = false)
    }

    /**
     * Saves a discovered device as a printer, starting from the device-wide defaults, then tries to
     * connect to it.
     *
     * Adding does not select on its own — it goes through [select] like a tap, so a printer only
     * becomes the active one by answering. Its paper and language are guesses until the test print
     * confirms them.
     */
    fun save(device: DiscoveredDevice) {
        val defaults = store.current()
        store.addPrinter(
            SavedPrinter(
                id          = device.address,
                displayName = device.name,
                method      = defaults.method,
                paper       = defaults.defaultPaper,
                language    = defaults.defaultLanguage,
                codePage    = defaults.defaultCodePage,
            )
        )
        select(device.address)
    }

    /**
     * Makes a printer the active one — but only if it answers.
     *
     * Selecting used to be a write and nothing more, so a printer that was off, flat or left at the
     * depot could sit there labelled as the one receipts go to, and the first anyone knew of it was a
     * client waiting at the counter. Now the tap opens a real connection first and the selection
     * follows the outcome: a refusal leaves the previous choice alone and marks this one, and tapping
     * again retries.
     *
     * A failure here never *un*-selects whatever was already working — only a successful probe ever
     * changes which printer is active.
     */
    fun select(id: String) {
        if (_state.value.connectingId != null) return
        val target = store.current().printers.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return

        _state.value = _state.value.copy(
            connectingId = id,
            failures     = _state.value.failures - id,
        )
        viewModelScope.launch {
            val link = gate.probe(target)
            _state.value = if (link is PrinterLink.Ready) {
                store.selectPrinter(target.id)
                _state.value.copy(connectingId = null, link = link)
            } else {
                val failure = (link as? PrinterLink.Blocked)?.reason ?: PrintFailure.UNREACHABLE
                _state.value.copy(
                    connectingId = null,
                    failures     = _state.value.failures + (target.id to failure),
                    // A radio that is off or unpermitted stops every printer, so it belongs in the
                    // banner. One printer not answering belongs on that printer's row.
                    link = if (failure.isDeviceWide()) PrinterLink.Blocked(null, failure) else _state.value.link,
                )
            }
        }
    }

    private fun PrintFailure.isDeviceWide() = this == PrintFailure.NO_BLUETOOTH ||
        this == PrintFailure.PERMISSION_DENIED || this == PrintFailure.ADAPTER_OFF

    fun rename(id: String, name: String) = store.renamePrinter(id, name)

    /**
     * Changes one printer's hardware settings.
     *
     * Per printer, not per app: a rep carrying an 80 mm counter unit and a 58 mm belt printer must not
     * re-pick the width every time they switch. None of the three can be read from the hardware, so
     * each is a guess until the test print confirms it.
     */
    fun configure(
        id      : String,
        paper   : com.distrigo.app.data.print.PaperSize? = null,
        language: com.distrigo.app.data.print.PrintLanguage? = null,
        codePage: com.distrigo.app.data.print.lang.PrinterCodePage? = null,
    ) = store.configurePrinter(id, paper, language, codePage)

    fun remove(id: String) {
        store.removePrinter(id)
        _state.value = _state.value.copy(failures = _state.value.failures - id)
        refresh()
    }

    /** Prints the calibration strip — the only way to confirm paper, language and code page. */
    fun testPrint(target: SavedPrinter) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            _state.value = when (val result = printer.printTest(target)) {
                PrintResult.Success -> _state.value.copy(
                    busy   = false,
                    link   = PrinterLink.Ready(target),
                    notice = "Test envoyé. Vérifiez la bande imprimée.",
                )
                // Into the banner, not a dialog: it names the problem, carries the button that fixes
                // it, and stays put while the user goes and switches the printer on.
                is PrintResult.Failed -> _state.value.copy(
                    busy = false,
                    link = PrinterLink.Blocked(target, result.failure),
                )
            }
        }
    }

    /**
     * Prints a dummy raster the size of a rasterised receipt and reports how long it took.
     *
     * Not a feature — an experiment, and the one that decides whether the whole receipt can be drawn
     * as a bitmap (the only way to print Arabic). It uses real paper, so the menu says how much.
     */
    fun benchmarkRaster(target: SavedPrinter) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            _state.value = when (val outcome = printer.benchmarkRaster(target)) {
                is RasterBenchmarkOutcome.Measured -> _state.value.copy(
                    busy   = false,
                    link   = PrinterLink.Ready(target),
                    notice = outcome.result.summary(),
                )
                is RasterBenchmarkOutcome.Failed -> _state.value.copy(
                    busy = false,
                    link = PrinterLink.Blocked(target, outcome.failure),
                )
            }
        }
    }

    /** Opens a connection without printing, to answer "is it there?" before a sale. */
    fun probe(target: SavedPrinter) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            val link = gate.probe(target)
            _state.value = _state.value.copy(
                busy   = false,
                link   = link,
                // A reachable printer leaves nothing on screen to prove it, so that one is worth
                // saying out loud. A blocked one is already in the banner.
                notice = (link as? PrinterLink.Ready)?.let { "${target.displayName} répond." },
            )
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    override fun onCleared() {
        // Leaving the screen must not leave the radio running an inquiry.
        scanner.cancelDiscovery()
        super.onCleared()
    }

    private fun applySettings(settings: PrintSettings) {
        _state.value = _state.value.copy(
            saved      = settings.printers,
            selectedId = settings.selectedPrinterId,
            method     = settings.method,
            link       = gate.check(settings.selectedPrinter),
            // A device that has just been saved belongs in the saved list, not in both.
            bonded     = if (settings.method == ConnectionMethod.BLUETOOTH)
                scanner.bonded().filterNot { isSaved(it.address, settings) } else emptyList(),
            discovered = _state.value.discovered.filterNot { isSaved(it.address, settings) },
        )
    }

    private fun addDiscovered(device: DiscoveredDevice) {
        val current = _state.value
        if (isSaved(device.address) || current.bonded.any { it.address == device.address }) return
        if (current.discovered.any { it.address == device.address }) return
        _state.value = current.copy(
            discovered = (current.discovered + device).sortedByDescending { it.looksLikePrinter },
        )
    }

    private fun isSaved(address: String, settings: PrintSettings = store.current()): Boolean =
        settings.printers.any { it.id.equals(address, ignoreCase = true) }

    private companion object {
        const val SCAN_CEILING_MS = 30_000L
    }
}

/**
 * The measurement, in the terms the decision turns on.
 *
 * The link rate is the number that matters — it is what a rasterised receipt would actually cost —
 * and the pacing is reported beside it because the two have opposite fixes: a slow link means the
 * architecture is not viable on this hardware, while a large pacing share means our own chunking is
 * too cautious for payloads this size and can simply be raised.
 */
private fun RasterBenchmarkResult.summary(): String {
    fun ko(bytes: Int) = String.format(java.util.Locale.FRENCH, "%.1f Ko", bytes / 1024.0)
    fun secs(ms: Long) = String.format(java.util.Locale.FRENCH, "%.1f s", ms / 1000.0)
    fun rate(bps: Double) = String.format(java.util.Locale.FRENCH, "%.1f Ko/s", bps / 1024.0)

    return buildString {
        appendLine("${ko(bytes)} en ${secs(elapsedMs)}")
        appendLine("Lien : ${rate(linkBytesPerSecond)}")
        appendLine("Cadencement interne : ${secs(pacingOverheadMs)}")
        appendLine()
        append("Vérifiez la bande : des variations de densité d'une bande à l'autre indiquent des saccades.")
    }
}
