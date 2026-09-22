package com.distrigo.app.data.print

/**
 * The paper a receipt is printed on.
 *
 * Thermal widths are not detectable — no ESC/POS status command reports print width, SPP and raw
 * port 9100 are both dumb byte pipes, and the model name is no help. So the width is chosen here and
 * confirmed by a test print. See docs/print_architecture.md §2.
 *
 * [A4] is the odd one out: it never reaches the thermal renderer at all. It keeps
 * `ReceiptPdfGenerator` + `PrintManager`, whose own printer discovery negotiates media size with the
 * print service, so nothing below applies to it.
 */
enum class PaperSize(val storageCode: String, val label: String, val kindLabel: String) {
    MM58("58mm", "58 mm", "Imprimante thermique"),
    MM80("80mm", "80 mm", "Imprimante thermique"),
    A4  ("a4",   "A4",    "Imprimante normale");

    val isThermal: Boolean get() = this != A4

    companion object {
        val DEFAULT = MM80

        fun fromStorage(code: String?): PaperSize =
            entries.firstOrNull { it.storageCode == code } ?: DEFAULT
    }
}

/**
 * Everything the layout engine and the renderers need to know about one paper width.
 *
 * All of it derives from 203 dpi, the universal thermal resolution: 8 dots per mm. The two numbers
 * that actually shape a receipt are [charsPerLine] — 32 and 48 — and [dotsPerLine] — 384 and 576.
 *
 * @param paperMm the physical roll width, which is what the user buys and what the preview's card is
 *   proportioned to. Wider than [printableMm]: the head does not reach the edges.
 * @param printableMm the width the head actually covers, which [dotsPerLine] is 8 dots per mm of.
 * @param charsPerLine Font A (12×24 dots) characters per line. The layout engine's unit of width.
 * @param charsPerLineSmall Font B (9×17). Not used by the current layout; recorded because the 58 mm
 *   single-line-table question in docs/print_architecture.md §12 turns on it.
 */
data class PaperProfile(
    val size             : PaperSize,
    val paperMm          : Int,
    val printableMm      : Int,
    val dotsPerLine      : Int,
    val charsPerLine     : Int,
    val charsPerLineSmall: Int,
    val dpi              : Int = 203,
) {
    /**
     * Raster width for an image, padded up to a whole number of bytes.
     *
     * ESC/POS `GS v 0` takes image rows as bytes of 8 horizontal dots, so a width that is not a
     * multiple of 8 has no representation. 384 and 576 both already are; the padding exists so a
     * future profile cannot quietly produce a raster the renderer would shear.
     */
    val rasterWidthDots: Int get() = (dotsPerLine / 8) * 8

    companion object {
        /** 58 mm roll: ~48 mm printable, 384 dots, 32 characters. */
        val MM58 = PaperProfile(
            size = PaperSize.MM58,
            paperMm = 58, printableMm = 48,
            dotsPerLine = 384, charsPerLine = 32, charsPerLineSmall = 42,
        )

        /** 80 mm roll: ~72 mm printable, 576 dots, 48 characters. */
        val MM80 = PaperProfile(
            size = PaperSize.MM80,
            paperMm = 80, printableMm = 72,
            dotsPerLine = 576, charsPerLine = 48, charsPerLineSmall = 64,
        )

        /**
         * Null for [PaperSize.A4], which has no thermal profile — the caller is expected to take the
         * PDF path instead, and a fabricated A4 "charsPerLine" would only let it forget to.
         */
        fun of(size: PaperSize): PaperProfile? = when (size) {
            PaperSize.MM58 -> MM58
            PaperSize.MM80 -> MM80
            PaperSize.A4   -> null
        }
    }
}
