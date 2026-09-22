package com.distrigo.app.ui.components

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.PrintResult
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.PrintSettingsStore
import com.distrigo.app.data.print.PrinterGate
import com.distrigo.app.data.print.ReceiptPrinter
import com.distrigo.app.data.print.ReceiptRasterizer
import com.distrigo.app.data.print.transport.PrintFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where a print attempt has got to. */
sealed interface ReceiptPrintStatus {
    data object Idle : ReceiptPrintStatus
    data object Printing : ReceiptPrintStatus
    data object Sent : ReceiptPrintStatus
    data class Failed(val failure: PrintFailure) : ReceiptPrintStatus
}

/**
 * Printing a receipt from the screens that show one.
 *
 * Only the thermal half lives here. A4 stays in the composable, because handing a PDF to
 * `PrintManager` wants the Activity's context and an application-scoped one is refused — see
 * ReceiptPreviewSheet, which does the branching.
 *
 * **The sale is already committed by the time anything here runs.** A printer that is off, out of
 * range or unconfigured must therefore never look like a failed sale: every path ends in a message
 * and the offer to share a PDF instead, and nothing is retried automatically.
 */
@HiltViewModel
class ReceiptPrintViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PrintSettingsStore,
) : ViewModel() {

    private val printer = ReceiptPrinter(context, PrinterGate(context))

    val settings: StateFlow<PrintSettings> = store.flow()

    private val _status = MutableStateFlow<ReceiptPrintStatus>(ReceiptPrintStatus.Idle)
    val status: StateFlow<ReceiptPrintStatus> = _status.asStateFlow()

    /**
     * Lays the receipt out for the selected printer's paper and sends it.
     *
     * Refuses quietly while a job is in flight: the button is disabled too, but a fast double tap
     * beats the recomposition and would otherwise print the receipt twice.
     */
    fun print(receipt: ReceiptData) {
        if (_status.value == ReceiptPrintStatus.Printing) return
        val current = settings.value
        if (PaperProfile.of(current.effectivePaper) == null) {
            // A4 never reaches here; the composable branches first. Reaching this means the caller
            // skipped that branch, and saying so beats printing nothing without explanation.
            _status.value = ReceiptPrintStatus.Failed(PrintFailure.NOT_CONFIGURED)
            return
        }

        _status.value = ReceiptPrintStatus.Printing
        viewModelScope.launch {
            _status.value = when (val result = printer.print(receipt, current.selectedPrinter)) {
                PrintResult.Success   -> ReceiptPrintStatus.Sent
                is PrintResult.Failed -> ReceiptPrintStatus.Failed(result.failure)
            }
        }
    }

    fun clear() {
        _status.value = ReceiptPrintStatus.Idle
    }
}
