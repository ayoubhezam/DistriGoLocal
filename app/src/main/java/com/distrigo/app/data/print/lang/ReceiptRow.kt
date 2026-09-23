package com.distrigo.app.data.print.lang

/**
 * Where a piece of text sits in the space it was given.
 *
 * [Start] and [End] follow the text's *own* direction rather than the page's: an Arabic line with
 * [Start] is flush right, a French one flush left. That is right for a paragraph — a line of Arabic
 * that began at the left edge would read as though it were indented.
 *
 * [Left] is the absolute one, and exists for a column rather than a paragraph. A list of product
 * names is read by running an eye down its left edge, and a column where the Arabic entries start at
 * the right and the French at the left has no edge to run down. It changes only where the text
 * begins: the Arabic is still shaped, still joined, and still reads right to left within itself.
 */
enum class RowAlign { Start, Center, End, Left }

/** How heavily a line is drawn. */
enum class RowWeight { Normal, Bold }

/** Character scale. [Large] is for the total, the one number everybody checks. */
enum class RowScale { Normal, Large }

/** A drawn rule's thickness, in dots. */
enum class RuleThickness(val dots: Int) { Thin(1), Thick(3) }

/**
 * One cell of a [ReceiptRow.Cells] row.
 *
 * @param fraction the share of the printable width this cell takes. The fractions in a row are
 *   expected to sum to 1; they are relative rather than absolute so the same layout serves 58 mm and
 *   80 mm without a second set of numbers.
 */
data class Cell(
    val text    : String,
    val fraction: Float,
    val align   : RowAlign = RowAlign.Start,
)

/**
 * One row of a laid-out receipt, in **dots rather than characters**.
 *
 * The earlier version of this type carried text already padded and wrapped to a fixed character
 * grid, which is what a printer's own font imposes. That model cannot express Arabic: the script is
 * proportional, joins its letters into contextual forms, and runs right to left, so there is no
 * column of cells to pad it into. ESC/POS cannot render it either — it has no bidi and no shaping —
 * so the receipt is drawn on an Android Canvas and sent as dots, and the layout stopped counting
 * characters.
 *
 * What survives is the principle: [com.distrigo.app.data.print.lang.ThermalLayout] produces this
 * list once, and the renderer and the preview both consume it. The preview shows the very bitmaps
 * that go to the printer, so the two cannot drift.
 *
 * Nothing here is measured or wrapped. Wrapping is the renderer's job, because only the renderer
 * knows what the glyphs are — and it gets it from `StaticLayout`, which wraps, shapes and reorders
 * in one step.
 */
sealed interface ReceiptRow {

    /** A run of text across the full width. Wrapped by the renderer if it does not fit. */
    data class Line(
        val text  : String,
        val align : RowAlign  = RowAlign.Start,
        val weight: RowWeight = RowWeight.Normal,
        val scale : RowScale  = RowScale.Normal,
    ) : ReceiptRow

    /**
     * Two runs pinned to opposite edges of the same row — a label and its amount.
     *
     * Kept apart from a two-cell [Cells] because the split is not a proportion: the right-hand value
     * takes exactly what it needs and the left takes the rest, which is what keeps a column of
     * amounts aligned while the labels beside them vary in length.
     */
    data class Columns(
        val left  : String,
        val right : String,
        val weight: RowWeight = RowWeight.Normal,
        val scale : RowScale  = RowScale.Normal,
    ) : ReceiptRow

    /**
     * A row divided into proportional cells — the item table, and the labelled header fields.
     *
     * Each cell wraps within its own width, and the row is as tall as its tallest cell. That is how a
     * long product name pushes its own row down without disturbing the figures beside it.
     */
    data class Cells(
        val cells : List<Cell>,
        val weight: RowWeight = RowWeight.Normal,
    ) : ReceiptRow

    /**
     * A horizontal rule.
     *
     * Drawn as a line rather than printed as a row of hyphens. The character version existed because
     * a character grid had nothing else to offer; a drawn rule is thinner, cleaner, and costs a
     * single dot row instead of a whole line of text.
     */
    data class Rule(val thickness: RuleThickness = RuleThickness.Thin) : ReceiptRow

    /** Vertical space, in dots. */
    data class Blank(val dots: Int) : ReceiptRow

    /**
     * A 1-bit image, already scaled and dithered to the paper's dot width — the logo.
     *
     * Still dithered rather than thresholded, unlike the text around it: Floyd–Steinberg is right for
     * a photographic mark and wrong for glyph edges, which is why the two take different paths into
     * the same [MonoRaster].
     */
    data class Raster(val raster: MonoRaster, val align: RowAlign = RowAlign.Center) : ReceiptRow

    /** Feed and cut. Ignored by printers without a cutter, which is most mobile ones. */
    data object Cut : ReceiptRow
}
