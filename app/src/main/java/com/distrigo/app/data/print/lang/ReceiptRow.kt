package com.distrigo.app.data.print.lang

/** Horizontal placement of a [ReceiptRow.Line] within the paper's character width. */
enum class RowAlign { Left, Center, Right }

/** How heavily a line is printed. [Bold] is ESC/POS emphasis, not a different font. */
enum class RowWeight { Normal, Bold }

/**
 * Character scale. [Double] is ESC/POS `GS !` double-width-and-height, which halves the characters
 * that fit on the line — the layout engine accounts for that, so a [RowScale.Double] line is laid out
 * against half the paper's [com.distrigo.app.data.print.PaperProfile.charsPerLine].
 */
enum class RowScale { Normal, Double }

/**
 * One row of a laid-out receipt.
 *
 * The whole point of this type: [ThermalLayout] produces a list of these once, and the ESC/POS
 * renderer, the label renderers and the on-screen preview all consume the same list. The preview is
 * then truthful by construction rather than by two layouts happening to agree — which is the state
 * `ReceiptPreviewSheet` and `ReceiptPdfGenerator` are already in, each carrying its own copy of the
 * same rules.
 *
 * Everything here is already measured against a paper width. A row never has to be re-wrapped, and a
 * renderer never has to know how wide the paper is to emit it.
 */
sealed interface ReceiptRow {

    /** A single line of text, already truncated to fit. */
    data class Line(
        val text  : String,
        val align : RowAlign  = RowAlign.Left,
        val weight: RowWeight = RowWeight.Normal,
        val scale : RowScale  = RowScale.Normal,
    ) : ReceiptRow

    /**
     * Text pinned to both edges of the same line, with the gap between them padded out.
     *
     * Kept apart from [Line] rather than pre-padded into one string because the label languages
     * position by dots, not by spaces, and a right-hand value that was spaced into place would land
     * wherever the label font happened to put it.
     */
    data class Columns(
        val left  : String,
        val right : String,
        val weight: RowWeight = RowWeight.Normal,
        val scale : RowScale  = RowScale.Normal,
    ) : ReceiptRow

    /** A full-width rule, repeating [char] across the paper. */
    data class Rule(val char: Char = '-') : ReceiptRow

    /** [count] empty lines. */
    data class Blank(val count: Int = 1) : ReceiptRow

    /**
     * A 1-bit image, already scaled and dithered to the paper's dot width — the logo.
     *
     * Rasterised in the layout, not in the renderer, so the preview draws the same speckles the paper
     * will carry.
     */
    data class Raster(val raster: MonoRaster, val align: RowAlign = RowAlign.Center) : ReceiptRow

    /**
     * A QR code, carried as its payload rather than as pixels.
     *
     * ESC/POS can emit this natively (`GS ( k`), which is sharper and a fraction of the bytes of a
     * raster; the preview and any raster-only fallback go through ZXing instead. Handing the renderer
     * a bitmap would throw that choice away.
     *
     * @param sizeDots the side length to aim for when it has to be rasterised.
     */
    data class Qr(val payload: String, val sizeDots: Int) : ReceiptRow

    /** Feed and cut. Ignored by printers without a cutter, which is most mobile ones. */
    data object Cut : ReceiptRow
}
