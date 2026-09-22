package com.distrigo.app.ui.settings.print

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.print.ConnectionMethod
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.PrintLanguage
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.PrintSettingsStore
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.data.print.lang.ThermalRaster
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
import java.io.File
import javax.inject.Inject

/**
 * What the preview needs to draw itself: the rows, and the paper they were laid out for.
 *
 * Null [rows] means A4, which has no thermal layout — the screen shows the page placeholder instead.
 */
data class PreviewState(
    val paper: PaperProfile?,
    val rows : List<ReceiptRow> = emptyList(),
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

    fun setPaper(paper: PaperSize) = store.update { it.copy(defaultPaper = paper) }

    fun setLanguage(language: PrintLanguage) = store.update { it.copy(defaultLanguage = language) }

    private fun buildPreview(paperSize: PaperSize, business: BusinessSettings): PreviewState {
        val paper = PaperProfile.of(paperSize) ?: return PreviewState(paper = null)
        val receipt = SAMPLE_RECEIPT.copy(
            businessName     = business.name,
            businessPhone    = business.phone,
            businessLogoPath = business.logoPath,
        )
        return PreviewState(
            paper = paper,
            rows  = ThermalLayout.layout(receipt, paper, logoRaster(business.logoPath, paper)),
        )
    }

    /**
     * The logo, scaled and dithered to this paper's dot width.
     *
     * Null when there is no logo, when the file is missing — a database restored without its photos has
     * a logo reference and no file — or when it cannot be decoded. The receipt then simply has no logo,
     * which is what the A4 renderer already does in the same situation.
     *
     * Cached on the path and the width together, because the same logo has to be re-dithered when the
     * paper changes: 384 dots and 576 dots are different images, not one image at two sizes.
     */
    private fun logoRaster(path: String?, paper: PaperProfile): MonoRaster? {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
        val key = file.path to paper.rasterWidthDots
        cachedLogo?.let { (cachedKey, raster) -> if (cachedKey == key) return raster }
        val raster = runCatching {
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
            // The logo is given the full printable width; a mark that wants to be smaller is smaller in
            // the source image, which is the only place the user can control it from.
            ThermalRaster.fromBitmap(bitmap, paper.rasterWidthDots)
        }.getOrNull()
        cachedLogo = key to raster
        return raster
    }

    private var cachedLogo: Pair<Pair<String, Int>, MonoRaster?>? = null
}
