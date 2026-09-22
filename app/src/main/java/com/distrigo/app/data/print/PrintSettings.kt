package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage

/** How the app reaches the printer. */
enum class ConnectionMethod(val storageCode: String, val label: String) {
    BLUETOOTH("bluetooth", "Bluetooth"),
    WIFI     ("wifi",      "Wi-Fi");

    companion object {
        val DEFAULT = BLUETOOTH

        fun fromStorage(code: String?): ConnectionMethod =
            entries.firstOrNull { it.storageCode == code } ?: DEFAULT
    }
}

/**
 * The command language the printer speaks.
 *
 * These are three machine classes, not three dialects of one thing: [ESC_POS] is a streaming receipt
 * language with no page concept, while [TSPL] and [CPCL] are page-based label languages that need the
 * page height before the first byte goes out. Picking the wrong one does not degrade the output, it
 * spools the commands out as literal text — which is why the choice is confirmed by a test print
 * rather than simply saved. See docs/print_architecture.md §4.
 */
enum class PrintLanguage(val storageCode: String, val label: String, val note: String) {
    ESC_POS("escpos", "ESC/POS", "Imprimantes de reçus (recommandé)"),
    TSPL   ("tspl",   "TSPL",    "Imprimantes d'étiquettes TSC"),
    CPCL   ("cpcl",   "CPCL",    "Imprimantes d'étiquettes Zebra / Citizen");

    /** Page-based: the renderer must know the total height before emitting. */
    val isPageBased: Boolean get() = this != ESC_POS

    companion object {
        val DEFAULT = ESC_POS

        fun fromStorage(code: String?): PrintLanguage =
            entries.firstOrNull { it.storageCode == code } ?: DEFAULT
    }
}

/**
 * A printer the user has saved, with the four properties that belong to the hardware rather than to
 * the app: its paper, its language, its code page and its address.
 *
 * Kept per printer because a rep carries two — an 80 mm unit at the counter and a 58 mm on the belt —
 * and must not re-pick the width every time they switch.
 *
 * Nothing writes these yet; [PrinterSelectionScreen] does, in phase 2. The field exists from the
 * start so the stored file does not need a migration to gain it.
 *
 * @param id stable identity: the MAC address for Bluetooth, `host:port` for Wi-Fi.
 */
data class SavedPrinter(
    val id         : String,
    val displayName: String,
    val method     : ConnectionMethod,
    val paper      : PaperSize,
    val language   : PrintLanguage,
    val codePage   : PrinterCodePage,
)

/**
 * This handset's printing configuration.
 *
 * Device-local, never synced — see [PrintSettingsStore] for why that matters.
 *
 * The three `default*` values are what a newly added printer starts from, and what the preview
 * renders while no printer is saved. Once a printer is selected its own properties win, so these stay
 * meaningful as defaults rather than becoming a second source of truth.
 */
data class PrintSettings(
    val method           : ConnectionMethod = ConnectionMethod.DEFAULT,
    val selectedPrinterId: String?          = null,
    val defaultPaper     : PaperSize        = PaperSize.DEFAULT,
    val defaultLanguage  : PrintLanguage    = PrintLanguage.DEFAULT,
    val defaultCodePage  : PrinterCodePage  = PrinterCodePage.DEFAULT,
    val printers         : List<SavedPrinter> = emptyList(),
) {
    val selectedPrinter: SavedPrinter?
        get() = selectedPrinterId?.let { id -> printers.firstOrNull { it.id == id } }

    /** The paper in force: the selected printer's, or the device default until one is selected. */
    val effectivePaper: PaperSize get() = selectedPrinter?.paper ?: defaultPaper

    /** The language in force, resolved the same way as [effectivePaper]. */
    val effectiveLanguage: PrintLanguage get() = selectedPrinter?.language ?: defaultLanguage

    /** The code page in force, resolved the same way as [effectivePaper]. */
    val effectiveCodePage: PrinterCodePage get() = selectedPrinter?.codePage ?: defaultCodePage
}
