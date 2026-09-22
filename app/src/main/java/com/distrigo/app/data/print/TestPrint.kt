package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.RowWeight

/**
 * The calibration strip — the closest thing to detecting a printer's paper width.
 *
 * Android cannot ask a printer how wide its paper is (docs/print_architecture.md §2), so this asks the
 * user instead, in the only terms that need no explanation: it prints rulers of exactly 32 and exactly
 * 48 characters and lets them see which one fits. A line that wraps onto a second row is a line wider
 * than the paper, and that is the answer.
 *
 * It deliberately builds its rows directly rather than through [lang.ThermalLayout], which measures
 * everything against the chosen profile. The whole point here is to emit lines that may be *too wide*,
 * so the wrap becomes visible.
 *
 * It doubles as the confirmation for the other two per-printer settings, because all three fail the
 * same way — silently, at the counter, on a real client's receipt:
 * - the **code page**, through [PrinterCodePage.ACCENT_PROBE]. Wrong page, and the accents come out as
 *   box-drawing characters.
 * - the **language**, by existing at all. On a printer expecting TSPL or CPCL, ESC/POS bytes spool out
 *   as visible garbage instead of a strip.
 */
object TestPrint {

    fun rows(paper: PaperProfile, language: PrintLanguage, codePage: PrinterCodePage): List<ReceiptRow> = buildList {
        add(ReceiptRow.Line("TEST D'IMPRESSION", RowAlign.Center, RowWeight.Bold))
        add(ReceiptRow.Line("DistriGo", RowAlign.Center))
        add(ReceiptRow.Rule())

        add(ReceiptRow.Columns("Papier", "${paper.size.label} (${paper.charsPerLine} car.)"))
        add(ReceiptRow.Columns("Langage", language.label))
        add(ReceiptRow.Columns("Encodage", codePage.label))
        add(ReceiptRow.Rule())

        // ── Width ──
        add(ReceiptRow.Line("LARGEUR DU PAPIER", RowAlign.Left, RowWeight.Bold))
        add(ReceiptRow.Line("Une seule de ces lignes doit"))
        add(ReceiptRow.Line("tenir sans revenir a la ligne :"))
        add(ReceiptRow.Blank())
        add(ReceiptRow.Line(rulerOfWidth(32)))
        add(ReceiptRow.Line("^ 58 mm (32)"))
        add(ReceiptRow.Blank())
        add(ReceiptRow.Line(rulerOfWidth(48)))
        add(ReceiptRow.Line("^ 80 mm (48)"))
        add(ReceiptRow.Blank())
        add(ReceiptRow.Line("La derniere ligne entiere"))
        add(ReceiptRow.Line("indique votre format."))
        add(ReceiptRow.Rule())

        // ── Accents ──
        add(ReceiptRow.Line("ACCENTS", RowAlign.Left, RowWeight.Bold))
        add(ReceiptRow.Line(PrinterCodePage.ACCENT_PROBE))
        add(ReceiptRow.Line("Si ces lettres sont fausses,"))
        add(ReceiptRow.Line("changez l'encodage."))
        add(ReceiptRow.Rule())

        // ── Density ──
        //
        // A full-width bar of solid black is what shows a head running out of heat or a roll that has
        // been in a hot van: it prints grey, or banded, long before text becomes unreadable.
        add(ReceiptRow.Line("DENSITE", RowAlign.Left, RowWeight.Bold))
        add(ReceiptRow.Rule('#'))
        add(ReceiptRow.Rule('#'))
        add(ReceiptRow.Blank())
        add(ReceiptRow.Line("Gras", RowAlign.Left, RowWeight.Bold))
        add(ReceiptRow.Line("Centre", RowAlign.Center))
        add(ReceiptRow.Line("Droite", RowAlign.Right))

        add(ReceiptRow.Blank(2))
        add(ReceiptRow.Cut)
    }

    /**
     * `1234567890...` ending in a bar, exactly [width] characters wide.
     *
     * The bar is the mark the user looks for: reached on one line, the paper is at least that wide;
     * pushed onto a second line, it is not.
     */
    private fun rulerOfWidth(width: Int): String =
        (1 until width).joinToString("") { (it % 10).toString() } + "|"
}
