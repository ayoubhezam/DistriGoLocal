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
 * [A] is what DistriGo prints. [B] is a third shorter per line and was tried for exactly that reason,
 * but 9×17 dots turned out to be too small to read comfortably on the counter — legibility is not a
 * saving. The rest of the compaction (tight feed, no blank lines, no QR) kept the paper it saved.
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
 * chosen [font] — **32 and 48** in Font A.
 *
 * @param paperMm the physical roll width, which is what the user buys and what the preview's card is
 *   proportioned to. Wider than [printableMm]: the head does not reach the edges.
 * @param printableMm the width the head actually covers, which [dotsPerLine] is 8 dots per mm of.
 * @param lineSpacingDots the feed between one line's top and the next, set with `ESC 3 n`. Derived
 *   from the font's own glyph height so it can never overlap; see [LINE_GAP_DOTS].
 */
data class PaperProfile(
    val size            : PaperSize,
    val paperMm         : Int,
    val printableMm     : Int,
    val dotsPerLine     : Int,
    val font            : PrinterFont = PrinterFont.A,
    val lineSpacingDots : Int = font.glyphHeightDots + LINE_GAP_DOTS,
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
         * The gap left below a line's glyphs, in dots.
         *
         * The feed is derived from the font rather than fixed, because a fixed one is only ever right
         * for one font: 20 dots suits Font B's 17-dot glyphs and **overlaps** Font A's 24, printing
         * each line into the descenders of the one above. Three dots is enough to keep them apart
         * without the airy look of the factory default, which is 30–34 and generous even for Font A —
         * so 27 still saves 3–7 dots on every line.
         */
        const val LINE_GAP_DOTS = 3

        /** 58 mm roll: ~48 mm printable, 384 dots — 32 characters in Font A. */
        val MM58 = PaperProfile(
            size = PaperSize.MM58,
            paperMm = 58, printableMm = 48, dotsPerLine = 384,
        )

        /** 80 mm roll: ~72 mm printable, 576 dots — 48 characters in Font A. */
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
