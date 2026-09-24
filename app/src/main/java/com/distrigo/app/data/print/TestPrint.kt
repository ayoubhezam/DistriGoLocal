package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.RowWeight
import com.distrigo.app.data.print.lang.TestLine

/**
 * The calibration strip — the closest thing to detecting a printer's paper width, and now also the
 * only thing that still prints in the printer's own font.
 *
 * Android cannot ask a printer how wide its paper is (docs/print_architecture.md §2), so this asks
 * the user instead, in the only terms that need no explanation: it prints rulers of exactly 32 and
 * exactly 48 characters and lets them see which fits. A line that wraps onto a second row is a line
 * wider than the paper, and that is the answer.
 *
 * **Why it stayed text while the receipt became dots.** The receipt is drawn and rasterised so that
 * Arabic can be shaped and reordered, which incidentally makes code pages and fonts irrelevant to
 * it. But a raster cannot diagnose: a printer that ignores `GS v 0` and a printer that is switched
 * off both produce blank paper. This strip goes out as ESC/POS text, so printing at all proves the
 * printer speaks it, and the accent line still proves the code page — which matters because the
 * strip itself is the thing a user is told to run when something is wrong.
 */
object TestPrint {

    fun lines(paper: PaperProfile, language: PrintLanguage, codePage: PrinterCodePage): List<TestLine> = buildList {
        add(TestLine("TEST D'IMPRESSION", bold = true, centered = true))
        add(TestLine("DistriGo", centered = true))
        add(TestLine(rule(paper.charsPerLine)))

        add(TestLine("Papier   : ${paper.size.label} (${paper.charsPerLine} car.)"))
        add(TestLine("Langage  : ${language.label}"))
        add(TestLine("Encodage : ${codePage.label}"))
        add(TestLine(rule(paper.charsPerLine)))

        // ── Width ──
        add(TestLine("LARGEUR DU PAPIER", bold = true))
        add(TestLine("Une seule de ces lignes doit"))
        add(TestLine("tenir sans revenir a la ligne :"))
        add(TestLine(rulerOfWidth(32)))
        add(TestLine("^ 58 mm (32)"))
        add(TestLine(rulerOfWidth(48)))
        add(TestLine("^ 80 mm (48)"))
        add(TestLine("La derniere ligne entiere"))
        add(TestLine("indique votre format."))
        add(TestLine(rule(paper.charsPerLine)))

        // ── Accents ──
        //
        // The reason this strip is still text. On a printer left in Kanji mode, "é" is read as the
        // lead byte of a GBK pair and two French letters print as one Chinese one; FS . cancels that,
        // and this line is how anyone checks it worked.
        add(TestLine("ACCENTS", bold = true))
        add(TestLine(PrinterCodePage.ACCENT_PROBE))
        add(TestLine("Si ces lettres sont fausses,"))
        add(TestLine("changez l'encodage."))
        add(TestLine(rule(paper.charsPerLine)))

        // ── Density ──
        //
        // A full-width bar of solid black shows a head running out of heat or a roll that has been in
        // a hot van: it prints grey, or banded, long before text becomes unreadable.
        add(TestLine("DENSITE", bold = true))
        add(TestLine("#".repeat(paper.charsPerLine)))
        add(TestLine("#".repeat(paper.charsPerLine)))
        add(TestLine("Gras", bold = true))
        add(TestLine("Centre", centered = true))
    }

    /**
     * The strip for a **label-language** printer, TSPL or CPCL: drawn and sent as dots, like the
     * receipt.
     *
     * Not the text strip above, because text in these grammars is a different command set with
     * different fonts — and what the strip must prove is exactly what the receipt will need: that the
     * printer accepts this language's image command, and how far across the paper the dots reach.
     * Anything coming out at all proves the first; a printer set to the wrong language prints the
     * commands as text, or nothing, which is the answer too.
     *
     * The ruler does the width. A code-page line would prove nothing here — there are no text bytes
     * on this path — so the Arabic line takes its place: it is what the drawing exists for.
     */
    fun drawnRows(paper: PaperProfile, language: PrintLanguage): List<ReceiptRow> = buildList {
        add(ReceiptRow.Line("TEST D'IMPRESSION", RowAlign.Center, RowWeight.Bold))
        add(ReceiptRow.Line("DistriGo", RowAlign.Center))
        add(ReceiptRow.Rule())
        add(ReceiptRow.Line("Papier : ${paper.size.label} (${paper.printableMm} mm imprimables)"))
        add(ReceiptRow.Line("Langage : ${language.label}"))
        add(ReceiptRow.Rule())

        add(ReceiptRow.Line("LARGEUR DU PAPIER", weight = RowWeight.Bold))
        add(ReceiptRow.Line("Un trait tous les 10 mm. Les grands traits marquent 48 mm et 72 mm."))
        add(ReceiptRow.Raster(widthRuler(paper)))
        add(ReceiptRow.Line("La règle doit finir au bord du papier, sans être coupée."))
        add(ReceiptRow.Rule())

        add(ReceiptRow.Line("ARABE", weight = RowWeight.Bold))
        add(ReceiptRow.Line("بطاطا محلية طازجة"))
        add(ReceiptRow.Line("Les lettres doivent être liées."))
        add(ReceiptRow.Rule())

        // A full-width block of solid black, for the same reason as the "#" lines above: a head short
        // of heat, or a roll that has been in a hot van, shows here first as grey or banding.
        add(ReceiptRow.Line("DENSITÉ", weight = RowWeight.Bold))
        add(ReceiptRow.Raster(solid(paper.rasterWidthDots, DENSITY_ROWS)))
        add(ReceiptRow.Blank(TEAR_OFF_DOTS))
    }

    /**
     * A millimetre ruler as wide as [paper]'s dots: a tick every millimetre, longer every 5 and 10,
     * and a full-height bar at each paper's printable edge — 48 mm and 72 mm.
     *
     * The bar at an edge sits on the last dots *inside* it, so the 48 mm bar is what a 58 mm head
     * prints last. Chosen too wide, the ruler is cut at the paper's edge and the 72 mm bar never
     * appears; chosen too narrow, it stops short and leaves white paper beyond it.
     */
    fun widthRuler(paper: PaperProfile): MonoRaster {
        val width = paper.rasterWidthDots
        val bits = ByteArray(width / 8 * RULER_ROWS)
        fun burn(x: Int, fromY: Int) {
            if (x !in 0 until width) return
            for (y in fromY until RULER_ROWS) {
                val i = y * (width / 8) + (x shr 3)
                bits[i] = (bits[i].toInt() or (1 shl (7 - (x and 7)))).toByte()
            }
        }
        // The baseline, two dots thick along the bottom.
        for (x in 0 until width) burn(x, RULER_ROWS - 2)
        var mm = 0
        while (mm * DOTS_PER_MM < width) {
            val tick = when {
                mm % 10 == 0 -> 22
                mm % 5 == 0  -> 14
                else         -> 7
            }
            burn(mm * DOTS_PER_MM, RULER_ROWS - tick)
            mm++
        }
        for (edgeMm in EDGE_MARKS_MM) {
            val last = edgeMm * DOTS_PER_MM - 1
            for (x in last - 2..last) burn(x, 0)
        }
        return MonoRaster(width, RULER_ROWS, bits)
    }

    private fun solid(width: Int, height: Int) =
        MonoRaster(width, height, ByteArray(width / 8 * height) { -1 })

    private const val DOTS_PER_MM = 8
    private const val RULER_ROWS = 40
    private const val DENSITY_ROWS = 48
    private const val TEAR_OFF_DOTS = 120

    /** The printable edges of 58 mm and 80 mm paper. */
    private val EDGE_MARKS_MM = listOf(48, 72)

    private fun rule(width: Int) = "-".repeat(width)

    /**
     * `1234567890...` ending in a bar, exactly [width] characters wide.
     *
     * The bar is the mark the user looks for: reached on one line, the paper is at least that wide;
     * pushed onto a second line, it is not.
     */
    private fun rulerOfWidth(width: Int): String =
        (1 until width).joinToString("") { (it % 10).toString() } + "|"
}
