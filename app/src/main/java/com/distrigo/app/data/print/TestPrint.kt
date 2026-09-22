package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage
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
