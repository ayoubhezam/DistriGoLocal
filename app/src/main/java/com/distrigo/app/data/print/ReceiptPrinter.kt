package com.distrigo.app.data.print

import android.content.Context
import com.distrigo.app.data.print.lang.EscPosRenderer
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.RowWeight
import com.distrigo.app.data.print.transport.PrintException
import com.distrigo.app.data.print.transport.PrintFailure

/** What a print attempt did. */
sealed interface PrintResult {
    data object Success : PrintResult
    data class Failed(val failure: PrintFailure) : PrintResult
}

/**
 * The one way anything in the app reaches a printer.
 *
 * Every caller — the test print here, the receipt buttons in phase 3 — goes through [print], so the
 * pre-flight checks, the renderer choice and the failure mapping happen once. A screen that opened its
 * own socket would eventually skip one of the three.
 */
class ReceiptPrinter(
    private val context: Context,
    private val gate   : PrinterGate,
) {

    /**
     * Renders [rows] for [printer] and sends them.
     *
     * The gate runs first and its refusal is returned as-is, so "Bluetooth is off" arrives as that
     * rather than as a connection timeout thirty seconds later.
     */
    suspend fun print(rows: List<ReceiptRow>, printer: SavedPrinter?): PrintResult {
        when (val link = gate.check(printer)) {
            is PrinterLink.Blocked -> return PrintResult.Failed(link.reason)
            PrinterLink.NotConfigured -> return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)
            else -> Unit
        }
        val target = printer ?: return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)

        val paper = PaperProfile.of(target.paper)
            // A4 has no thermal profile and no business on this path: it belongs to
            // ReceiptPdfGenerator and Android's print dialog. Phase 3 branches before calling here.
            ?: return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)

        val bytes = when (target.language) {
            PrintLanguage.ESC_POS -> EscPosRenderer.render(rows, paper, target.codePage)
            // Phase 5. Sending ESC/POS to a label printer would spool the commands out as text, so
            // this refuses instead of printing something the user has to throw away.
            PrintLanguage.TSPL, PrintLanguage.CPCL -> return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)
        }

        return try {
            // The gate owns the choice of pipe, because it is also the thing that decided the printer
            // was reachable — one place deciding "Bluetooth or network" means the check and the send
            // cannot disagree about which one this printer is.
            gate.transportFor(target).send(target.id, bytes)
            PrintResult.Success
        } catch (e: PrintException) {
            PrintResult.Failed(e.failure)
        }
    }

    /**
     * Sends a dummy raster the size of a rasterised receipt and times it.
     *
     * The one experiment that decides whether the Canvas architecture is viable: ~40 KB of dots
     * against ~1.5 KB of text, over a link that may be a 9 600-baud serial bridge. See
     * [RasterBenchmark] for what the pattern is and why.
     *
     * Only the send is timed. Building and rendering the pattern is a handful of milliseconds on the
     * phone and would only blur the number that matters.
     */
    suspend fun benchmarkRaster(
        printer    : SavedPrinter?,
        targetBytes: Int = RasterBenchmark.DEFAULT_TARGET_BYTES,
    ): RasterBenchmarkOutcome {
        val target = printer ?: return RasterBenchmarkOutcome.Failed(PrintFailure.NOT_CONFIGURED)
        when (val link = gate.check(target)) {
            is PrinterLink.Blocked -> return RasterBenchmarkOutcome.Failed(link.reason)
            PrinterLink.NotConfigured -> return RasterBenchmarkOutcome.Failed(PrintFailure.NOT_CONFIGURED)
            else -> Unit
        }
        val paper = PaperProfile.of(target.paper)
            ?: return RasterBenchmarkOutcome.Failed(PrintFailure.NOT_CONFIGURED)

        val rows = listOf(
            ReceiptRow.Line("TEST RASTER", RowAlign.Center, RowWeight.Bold),
            ReceiptRow.Line("${targetBytes / 1024} Ko — ${paper.size.label}", RowAlign.Center),
            ReceiptRow.Raster(RasterBenchmark.pattern(paper, targetBytes)),
            ReceiptRow.Blank(2),
            ReceiptRow.Cut,
        )
        val bytes = EscPosRenderer.render(rows, paper, target.codePage)
        val transport = gate.transportFor(target)

        return try {
            val startedAt = System.nanoTime()
            transport.send(target.id, bytes)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            RasterBenchmarkOutcome.Measured(
                RasterBenchmarkResult(
                    bytes            = bytes.size,
                    elapsedMs        = elapsedMs,
                    pacingOverheadMs = transport.pacing.overheadMs(bytes.size),
                )
            )
        } catch (e: PrintException) {
            RasterBenchmarkOutcome.Failed(e.failure)
        }
    }

    /** The calibration strip of [TestPrint], for [printer]'s current paper, language and code page. */
    suspend fun printTest(printer: SavedPrinter?): PrintResult {
        val target = printer ?: return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)
        val paper = PaperProfile.of(target.paper) ?: return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)
        return print(TestPrint.rows(paper, target.language, target.codePage), target)
    }
}
