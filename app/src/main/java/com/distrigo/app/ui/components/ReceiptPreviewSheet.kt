package com.distrigo.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

private val Ink       = Color(0xFF14213D)
private val InkLight  = Color(0xFF1B3FCF)
private val Muted     = Color(0xFF64748B)
private val Faint     = Color(0xFFE2E8F0)
private val TableHead = Color(0xFF0F1E3D)

private fun isVenteReceipt(documentTitle: String) = documentTitle.startsWith("Vente")
private fun badgeLabel(documentTitle: String) = if (isVenteReceipt(documentTitle)) "REÇU DE VENTE" else "BON D'ACHAT"
private fun referenceNumber(documentTitle: String) =
    documentTitle.substringAfter("#", missingDelimiterValue = documentTitle).trim()

private fun nbColisText(item: ReceiptLineItem): String = item.nbColis?.toString() ?: "-"
private fun unitePerColisText(item: ReceiptLineItem): String =
    if (item.unitLabel == "pièce") item.unitePerColis?.toString() ?: "-" else "-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewSheet(
    receipt          : ReceiptData,
    onDismiss        : () -> Unit,
    onShareRequested : () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateOnly = receipt.dateLabel.substringBefore(" ")
    val timeOnly = receipt.dateLabel.substringAfter(" ", "")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFFF1F5F9)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        )  {
            Text(
                "APERÇU DU REÇU",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = Muted,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                // ── Header: logo + business name / badge ──
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        LogoBox(receipt.businessLogoPath)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            receipt.businessName,
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = InkLight,
                            maxLines   = 1,
                            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Ink)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(badgeLabel(receipt.documentTitle), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Faint, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                InfoLine("N°", referenceNumber(receipt.documentTitle))
                InfoLine("Date", dateOnly)
                if (timeOnly.isNotBlank()) InfoLine("Heure", timeOnly)
                InfoLine(receipt.partyLabel, receipt.partyName)

                Spacer(Modifier.height(14.dp))

                // ── Table header (6 colonnes, noms complets) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TableHead)
                        .padding(horizontal = 8.dp, vertical = 9.dp)
                ) {
                    TableHeadCell("N°", 0.3f)
                    TableHeadCell("Nom", 1.6f)
                    TableHeadCell("Nombre colis", 0.9f)
                    TableHeadCell("Unité/colis", 0.9f)
                    TableHeadCell("Prix unitaire", 1.0f, TextAlign.End)
                    TableHeadCell("Total", 1.0f, TextAlign.End)
                }

                receipt.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", fontSize = 10.5.sp, color = Muted, modifier = Modifier.weight(0.3f))
                        Text(item.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1.6f))
                        Text(nbColisText(item), fontSize = 10.5.sp, color = Muted, modifier = Modifier.weight(0.9f))
                        Text(unitePerColisText(item), fontSize = 10.5.sp, color = Muted, modifier = Modifier.weight(0.9f))
                        Text("%.2f".format(item.unitPrice), fontSize = 10.5.sp, color = Muted, textAlign = TextAlign.End, modifier = Modifier.weight(1.0f))
                        Text("%.2f".format(item.totalPrice), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkLight, textAlign = TextAlign.End, modifier = Modifier.weight(1.0f))
                    }
                    if (index < receipt.items.lastIndex) HorizontalDivider(color = Faint, thickness = 0.5.dp)
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = InkLight.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(Modifier.height(10.dp))

                // ── TOTAL — محاذاة يمين، متقاربة ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Spacer(Modifier.width(10.dp))
                    Text("${"%.2f".format(receipt.total)} DA", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = InkLight)
                }

                if (receipt.paid > 0 || receipt.balance != receipt.total) {
                    Spacer(Modifier.height(8.dp))
                    InfoLineEnd("Payé", "${"%.2f".format(receipt.paid)} DA")
                    if (receipt.balance > 0) InfoLineEnd("Reste", "${"%.2f".format(receipt.balance)} DA", Color(0xFFD97706))
                    else InfoLineEnd("Statut", "Réglé", Color(0xFF059669))
                }

                Spacer(Modifier.height(12.dp))
                Text("Arrêté à la somme de :", fontSize = 10.sp, color = Muted)
                Text(receipt.amountInWords, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink)

                receipt.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Note : $it", fontSize = 11.sp, color = Muted)
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Signature", fontSize = 10.sp, color = Muted)
                        Spacer(Modifier.height(28.dp))
                        HorizontalDivider(modifier = Modifier.width(90.dp), color = Faint)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Merci pour votre confiance !", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkLight)
                        Text("À bientôt", fontSize = 10.sp, color = Muted)
                    }
                    val qrBitmap = remember(receipt.qrContent) { QrCodeGenerator.generate(receipt.qrContent, 160) }
                    Image(
                        bitmap             = qrBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val file = ReceiptPdfGenerator.generate(context, receipt)
                        printReceiptPdf(context, file, receipt.documentTitle)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Imprimer le reçu", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick  = onShareRequested,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Partager (WhatsApp / PDF)", color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LogoBox(logoPath: String?) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE3F2FD)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = remember(logoPath) {
            logoPath?.let { path -> File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
        }
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                contentScale       = ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = InkLight, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color = Ink) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier           = Modifier.padding(vertical = 2.dp)
    ) {
        Text("$label :", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}
@Composable
private fun InfoLineEnd(label: String, value: String, valueColor: Color = Ink) {
    Row(
        modifier           = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment  = Alignment.CenterVertically
    ) {
        Text("$label :", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RowScope.TableHeadCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = align, modifier = Modifier.weight(weight))
}

// ── منطق الطباعة الفعلي (بدون تغيير) ──

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
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(documentName, PdfPrintAdapter(pdfFile, documentName), PrintAttributes.Builder().build())
}