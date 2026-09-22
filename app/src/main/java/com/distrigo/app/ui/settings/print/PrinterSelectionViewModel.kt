package com.distrigo.app.ui.settings.print

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.print.PrintResult
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.PrintSettingsStore
import com.distrigo.app.data.print.PrinterGate
import com.distrigo.app.data.print.PrinterLink
import com.distrigo.app.data.print.ReceiptPrinter
import com.distrigo.app.data.print.SavedPrinter
import com.distrigo.app.data.print.discovery.BluetoothScanner
import com.distrigo.app.data.print.discovery.DiscoveredDevice
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

    private val scanner = BluetoothScanner(context)
    private val gate    = PrinterGate(context)
    private val printer = ReceiptPrinter(context, gate)

    private val _state = MutableStateFlow(PrinterSelectionState())
    val state: StateFlow<PrinterSelectionState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            store.flow().collect { settings -> applySettings(settings) }
        }
    }

    /** Re-reads what needs a permission, after the screen has been granted one. */
    fun refresh() {
        _state.value = _state.value.copy(
            bonded = scanner.bonded().filterNot { device -> isSaved(device.address) },
            link   = gate.check(store.current().selectedPrinter),
        )
    }

    fun startScan() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, discovered = emptyList())
        scanJob = viewModelScope.launch {
            // Android ends an inquiry on its own after about twelve seconds, but a radio that never
            // reports finished would otherwise leave the button spinning forever. The ceiling is the
            // backstop, not the schedule.
            withTimeoutOrNull(SCAN_CEILING_MS) {
                scanner.discover()
                    .onCompletion { _state.value = _state.value.copy(scanning = false) }
                    .collect { device -> addDiscovered(device) }
            }
            _state.value = _state.value.copy(scanning = false)
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanner.cancelDiscovery()
        _state.value = _state.value.copy(scanning = false)
    }

    /**
     * Saves a discovered device as a printer, starting from the device-wide defaults.
     *
     * Its paper and language are guesses until the test print confirms them, which is why adding one
     * leads straight to that.
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
    }

    fun select(id: String) = store.selectPrinter(id)

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
            link       = gate.check(settings.selectedPrinter),
            // A device that has just been saved belongs in the saved list, not in both.
            bonded     = scanner.bonded().filterNot { isSaved(it.address, settings) },
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
