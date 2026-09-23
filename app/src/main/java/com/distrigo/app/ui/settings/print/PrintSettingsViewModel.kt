package com.distrigo.app.ui.settings.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.print.ConnectionMethod
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.PrintLanguage
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.PrintSettingsStore
import com.distrigo.app.data.print.ReceiptRasterizer
import com.distrigo.app.data.model.BusinessSettings
import com.distrigo.app.data.repository.BusinessSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the preview needs to draw itself: the rows, and the paper they were laid out for.
 *
 * A null [paper] means A4, which has no thermal layout — the screen shows the page placeholder instead.
 */
data class PreviewState(
    val paper  : PaperProfile?,
    val rows   : List<PreviewRow> = emptyList(),
)

/**
 * The printer half of "Reçus et impression", and the live preview.
 *
 * The preview is built here rather than in the composable because it decodes and dithers the logo,
 * which is file I/O and a per-dot loop over the image — work that has no business running inside a
 * recomposition. The receipt settings screen this one absorbed used to decode the logo with
 * BitmapFactory inside the composable, once per keystroke in the fields below it; that mistake is not
 * worth repeating one screen over.
 */
@HiltViewModel
class PrintSettingsViewModel @Inject constructor(
    private val store: PrintSettingsStore,
    businessRepository: BusinessSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<PrintSettings> = store.flow()

    /**
     * The sample receipt, laid out for the chosen paper, carrying the user's own business header.
     *
     * Recomputed when the paper changes — the layout and the logo's dot width both depend on it — and
     * when the business identity changes, so typing a shop name shows up on the paper as it is typed.
     */
    val preview: StateFlow<PreviewState> =
        combine(store.flow(), businessRepository.observe()) { print, business ->
            print.effectivePaper to business
        }
            .map { (paperSize, business) -> buildPreview(paperSize, business) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewState(PaperProfile.of(PaperSize.DEFAULT)))

    fun setMethod(method: ConnectionMethod) = store.update { it.copy(method = method) }

    /**
     * Sets the paper, on whatever the paper currently *belongs* to.
     *
     * Paper is a property of the printer, not of the app — a rep carries an 80 mm counter unit and a
     * 58 mm belt printer. So with a printer selected this configures that printer, and only with none
     * selected does it set the device default that the next added printer will start from.
     *
     * Writing the default unconditionally is what made the preview look frozen: the preview renders
     * `effectivePaper`, which is the selected printer's, so changing the default moved the chip and
     * nothing else. The control now always edits the value it is displaying.
     */
    fun setPaper(paper: PaperSize) = withSelected(
        onPrinter = { id -> store.configurePrinter(id, paper = paper) },
        onDefault = { store.update { it.copy(defaultPaper = paper) } },
    )

    fun setLanguage(language: PrintLanguage) = withSelected(
        onPrinter = { id -> store.configurePrinter(id, language = language) },
        onDefault = { store.update { it.copy(defaultLanguage = language) } },
    )

    private fun withSelected(onPrinter: (String) -> Unit, onDefault: () -> Unit) {
        val selected = store.current().selectedPrinter
        if (selected != null) onPrinter(selected.id) else onDefault()
    }

    private fun buildPreview(paperSize: PaperSize, business: BusinessSettings): PreviewState {
        val paper = PaperProfile.of(paperSize) ?: return PreviewState(paper = null)
        val receipt = SAMPLE_RECEIPT.copy(
            businessName     = business.name,
            businessPhone    = business.phone,
            businessLogoPath = business.logoPath,
        )
        // The very bitmaps the printer would receive, not a second drawing of them.
        return PreviewState(paper = paper, rows = ReceiptRasterizer.rasters(receipt, paper).toPreviewRows())
    }

}
