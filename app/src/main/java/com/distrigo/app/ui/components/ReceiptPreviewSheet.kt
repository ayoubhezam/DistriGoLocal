package com.distrigo.app.ui.components

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.data.print.message
import com.distrigo.app.data.print.transport.PrintFailure
import com.distrigo.app.ui.settings.print.A4PreviewPlaceholder
import com.distrigo.app.ui.settings.print.PreviewRow
import com.distrigo.app.ui.settings.print.ThermalReceiptPreview
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch

private val Ink   = Color(0xFF14213D)
private val Muted = Color(0xFF64748B)

internal fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", v)

/**
 * The receipt, exactly as it will come out of the printer.
 *
 * This used to draw its own version of the receipt in Compose — a second layout, maintained
 * alongside the one that actually printed, agreeing with it only because somebody kept checking.
 * It now shows the **same bitmaps** the printer receives: the layout is drawn once on a Canvas and
 * reduced to dots, and both this and the print button consume that. There is nothing left to drift.
 *
 * It is also the only way to preview Arabic honestly, since the shaping and the right-to-left order
 * come from `StaticLayout` inside the renderer.
 *
 * @param onShareRequested the PDF escape hatch, offered **only when printing fails**. The sale is
 *   already recorded by then, so the rep must never be left with no way to hand the client anything.
 *   The sheet itself no longer offers sharing — each detail screen has its own button for that, and
 *   a preview of the paper is not the place to decide to send a PDF instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewSheet(
    receipt          : ReceiptData,
    onDismiss        : () -> Unit,
    onShareRequested : () -> Unit,
    printViewModel   : ReceiptPrintViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var generatingPdf by remember { mutableStateOf(false) }

    val printSettings by printViewModel.settings.collectAsState()
    val printStatus   by printViewModel.status.collectAsState()

    val isA4 = printSettings.effectivePaper == PaperSize.A4
    val busy = generatingPdf || printStatus == ReceiptPrintStatus.Printing
    val paper = PaperProfile.of(printSettings.effectivePaper)

    // Drawing every row costs a few tens of milliseconds, so it happens off the main thread and the
    // sheet shows a spinner until it lands. Re-run when the paper changes, because 384 dots and 576
    // dots are different images rather than one image at two sizes.
    val rows: List<PreviewRow>? by produceState<List<PreviewRow>?>(null, receipt, printSettings.effectivePaper) {
        value = printViewModel.preview(receipt)
    }

    // Success leaves nothing on screen to show for itself, and a dialog for it would be one more tap
    // between the rep and the next client.
    LaunchedEffect(printStatus) {
        if (printStatus == ReceiptPrintStatus.Sent) {
            Toast.makeText(context, "Reçu envoyé à l'imprimante", Toast.LENGTH_SHORT).show()
            printViewModel.clear()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFFF1F5F9),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "APERÇU DU REÇU",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = Muted,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    paper == null      -> A4PreviewPlaceholder()
                    rows == null       -> CircularProgressIndicator(
                        modifier = Modifier.padding(48.dp),
                        color    = Ink,
                    )
                    else               -> ThermalReceiptPreview(rows = rows!!, paper = paper)
                }
            }

            Spacer(Modifier.height(20.dp))

            // The paper decides the route, without asking. A4 keeps the PDF and Android's own print
            // dialog — the only path where the OS negotiates the media size with the printer for us.
            // 58/80 mm goes to the thermal printer.
            //
            // printReceiptPdf needs the Activity's context (PrintManager refuses an
            // application-scoped one), which is why that half stays here rather than moving into the
            // ViewModel with the thermal half.
            fun startPrint() {
                if (busy) return
                if (isA4) {
                    generatingPdf = true
                    scope.launch {
                        try {
                            val file = ReceiptPdfGenerator.generate(context, receipt)
                            printReceiptPdf(context, file, receipt.documentTitle)
                        } finally {
                            generatingPdf = false
                        }
                    }
                } else {
                    printViewModel.print(receipt)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Button(
                    // The guard is doubled with the disabled state on purpose: the thermal send takes
                    // seconds, and a fast second tap beats the recomposition that would have greyed
                    // the button out.
                    onClick  = ::startPrint,
                    enabled  = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isA4) "Préparation…" else "Impression…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Imprimer le reçu", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Where it is about to go. Printing is the one action here whose outcome depends on a
                // setting made on another screen, so the button says which one is in force rather
                // than letting the paper be a surprise.
                Spacer(Modifier.height(8.dp))
                Text(
                    printTargetLabel(printSettings),
                    fontSize  = 11.sp,
                    color     = Muted,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ── When the printer will not ──
    //
    // The sale is already recorded by the time this can appear, so it is never phrased as a failure
    // of the sale and never leaves the user stuck: sharing the PDF is always offered beside the
    // retry, and dismissing it simply leaves the receipt on screen.
    (printStatus as? ReceiptPrintStatus.Failed)?.let { failed ->
        val message = failed.failure.message()
        AlertDialog(
            onDismissRequest = printViewModel::clear,
            title = { Text(message.title) },
            text  = {
                Text(
                    if (failed.failure == PrintFailure.NOT_CONFIGURED)
                        "Choisissez une imprimante dans Paramètres › Reçus et impression, " +
                            "ou partagez le reçu en PDF."
                    else message.detail
                )
            },
            confirmButton = {
                // Nothing to retry until a printer is chosen, and that is done on another screen.
                if (failed.failure != PrintFailure.NOT_CONFIGURED) {
                    TextButton(onClick = {
                        printViewModel.clear()
                        printViewModel.print(receipt)
                    }) { Text("Réessayer") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    printViewModel.clear()
                    onShareRequested()
                }) { Text("Partager en PDF") }
            },
        )
    }
}

/** What the print button is about to do, in the words of the setting that decides it. */
private fun printTargetLabel(settings: PrintSettings): String = when {
    settings.effectivePaper == PaperSize.A4 -> "Format A4 — via l'impression Android"
    settings.selectedPrinter != null ->
        "${settings.selectedPrinter!!.displayName} · ${settings.effectivePaper.label}"
    else -> "Aucune imprimante — Paramètres › Reçus et impression"
}

/**
 * Hands a rendered PDF to Android's print dialog.
 *
 * The A4 path only. A thermal receipt never comes through here — it is drawn and sent as dots.
 */
private class PdfPrintAdapter(
    private val pdfFile     : File,
    private val documentName: String
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?, destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?, callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

fun printReceiptPdf(context: Context, pdfFile: File, documentName: String) {
    // A4 is declared rather than left to the dialog's default, which on this phone is US Letter —
    // so a user who chose "A4" in the settings was handed a print dialog offering Letter. The PDF is
    // drawn at 595x842 pt, which is A4, so this is also simply the truth about the document.
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .build()
    printManager.print(documentName, PdfPrintAdapter(pdfFile, documentName), attributes)
}
