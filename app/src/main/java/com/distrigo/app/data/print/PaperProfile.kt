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
 * Which of the printer's two built-in fonts a receipt is set in.
 *
 * Every ESC/POS printer carries exactly these two, selected with `ESC M n`. They are bitmap fonts of
 * fixed size — there is no scale between them, and no third choice.
 *
 * [B] is what DistriGo prints. It is smaller and a receipt set in it is materially shorter, which is
 * what a shop buying rolls by the box cares about; the trade is that 9×17 dots is small print, and
 * anyone who finds it too small is looking for [A].
 *
 * @param glyphWidthDots the character cell's width. Together with [com.distrigo.app.data.print.PaperProfile.dotsPerLine]
 *   this is what fixes the characters per line, and it is also the unit the preview scales its line
 *   spacing against.
 */
enum class PrinterFont(val escPosSelector: Int, val glyphWidthDots: Int, val glyphHeightDots: Int) {
    A(0, 12, 24),
    B(1, 9, 17),
}

/**
 * Everything the layout engine and the renderers need to know about one paper width.
 *
 * All of it derives from 203 dpi, the universal thermal resolution: 8 dots per mm. The number that
 * actually shapes a receipt is [charsPerLine], which follows from the paper's [dotsPerLine] and the
 * chosen [font] — **42 and 64** in Font B, where it used to be 32 and 48 in Font A.
 *
 * @param paperMm the physical roll width, which is what the user buys and what the preview's card is
 *   proportioned to. Wider than [printableMm]: the head does not reach the edges.
 * @param printableMm the width the head actually covers, which [dotsPerLine] is 8 dots per mm of.
 * @param lineSpacingDots the feed between one line's top and the next, set with `ESC 3 n`. The
 *   printer's own default is roughly 30–34 dots, sized for Font A's 24-dot glyphs and generous even
 *   for those; against Font B's 17 it is a third of the paper spent on white space. See
 *   [COMPACT_LINE_SPACING].
 */
data class PaperProfile(
    val size            : PaperSize,
    val paperMm         : Int,
    val printableMm     : Int,
    val dotsPerLine     : Int,
    val font            : PrinterFont = PrinterFont.B,
    val lineSpacingDots : Int = COMPACT_LINE_SPACING,
    val dpi             : Int = 203,
) {
    /** Characters per line in the chosen [font]: what the layout engine measures everything against. */
    val charsPerLine: Int get() = dotsPerLine / font.glyphWidthDots

    /**
     * Raster width for an image, padded down to a whole number of bytes.
     *
     * ESC/POS `GS v 0` takes image rows as bytes of 8 horizontal dots, so a width that is not a
     * multiple of 8 has no representation. 384 and 576 both already are; the rounding exists so a
     * future profile cannot quietly produce a raster the renderer would shear.
     */
    val rasterWidthDots: Int get() = (dotsPerLine / 8) * 8

    companion object {
        /**
         * The feed between lines, in dots.
         *
         * Chosen against Font B's 17-dot glyphs: 20 leaves three dots of gap, which is enough to keep
         * descenders off the next line's capitals without the airy look of the factory default. At 203
         * dpi it saves roughly 1.7 mm of paper on every single line — about 5 cm on a twenty-line
         * receipt, which is the difference between two rolls and three over a month.
         */
        const val COMPACT_LINE_SPACING = 20

        /** 58 mm roll: ~48 mm printable, 384 dots — 42 characters in Font B. */
        val MM58 = PaperProfile(
            size = PaperSize.MM58,
            paperMm = 58, printableMm = 48, dotsPerLine = 384,
        )

        /** 80 mm roll: ~72 mm printable, 576 dots — 64 characters in Font B. */
        val MM80 = PaperProfile(
            size = PaperSize.MM80,
            paperMm = 80, printableMm = 72, dotsPerLine = 576,
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
