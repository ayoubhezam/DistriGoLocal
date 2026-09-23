package com.distrigo.app.data.print

import android.content.Context
import com.distrigo.app.data.print.lang.EscPosRenderer
import com.distrigo.app.data.print.transport.PrintException
import com.distrigo.app.data.print.transport.PrintFailure
import com.distrigo.app.ui.components.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * Draws [receipt] and sends it as dots.
     *
     * Everything the printer receives for a receipt is now an image, because ESC/POS cannot shape or
     * reorder Arabic. The drawing happens here rather than in the caller so that there is exactly one
     * path from a receipt to paper, and so the preview can take the same one.
     */
    suspend fun print(receipt: ReceiptData, printer: SavedPrinter?): PrintResult =
        send(printer) { paper -> EscPosRenderer.renderRaster(ReceiptRasterizer.rasters(receipt, paper)) }

    /**
     * Resolves the printer, renders with [bytes] and sends.
     *
     * The gate runs first and its refusal is returned as-is, so "Bluetooth is off" arrives as that
     * rather than as a connection timeout thirty seconds later.
     */
    private suspend fun send(printer: SavedPrinter?, bytes: (PaperProfile) -> ByteArray): PrintResult {
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

        // Phase 5. Sending ESC/POS to a label printer would spool the commands out as text, so this
        // refuses rather than printing something the user has to throw away.
        if (target.language != PrintLanguage.ESC_POS) return PrintResult.Failed(PrintFailure.NOT_CONFIGURED)

        // Off the caller's thread, which is Main for every caller today. For a receipt this is the
        // whole drawing — the logo decoded and dithered, every row laid out on a Canvas — and the
        // transport only moves to IO inside send(), after this argument has already been evaluated.
        val payload = withContext(Dispatchers.Default) { bytes(paper) }

        return try {
            // The gate owns the choice of pipe, because it is also the thing that decided the printer
            // was reachable — one place deciding "Bluetooth or network" means the check and the send
            // cannot disagree about which one this printer is.
            gate.transportFor(target).send(target.id, payload)
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

        val bytes = EscPosRenderer.renderRaster(listOf(RasterBenchmark.pattern(paper, targetBytes)))
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

    /**
     * The calibration strip of [TestPrint], for [printer]'s current paper, language and code page.
     *
     * The one thing still printed in the printer's own font: a raster cannot tell a printer that
     * ignores `GS v 0` from one that is switched off, and both produce blank paper.
     */
    suspend fun printTest(printer: SavedPrinter?): PrintResult = send(printer) { paper ->
        val target = printer!!
        EscPosRenderer.renderText(
            TestPrint.lines(paper, target.language, target.codePage), paper, target.codePage,
        )
    }
}
