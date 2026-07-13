package com.distrigo.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ReceiptPdfGenerator {

    private const val PAGE_WIDTH  = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN      = 32f

    private fun formatAmount(value: Double): String = "%.2f".format(value)

    private fun badgeLabel(documentTitle: String) =
        if (documentTitle.startsWith("Vente")) "REÇU DE VENTE" else "BON D'ACHAT"

    private fun referenceNumber(documentTitle: String) =
        documentTitle.substringAfter("#", missingDelimiterValue = documentTitle).trim()

    private fun nbColisText(item: ReceiptLineItem): String = item.nbColis?.toString() ?: "-"
    private fun unitePerColisText(item: ReceiptLineItem): String =
        if (item.unitLabel == "pièce") item.unitePerColis?.toString() ?: "-" else "-"

    fun generate(context: Context, receipt: ReceiptData): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val ink       = Color.rgb(20, 33, 61)
        val inkLight  = Color.rgb(27, 63, 207)
        val muted     = Color.rgb(100, 116, 139)
        val faint     = Color.rgb(226, 232, 240)
        val tableHead = Color.rgb(15, 30, 61)

        val businessName = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkLight; textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val badgeText     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        val label         = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 10.5f }
        val labelR        = Paint(label).apply { textAlign = Paint.Align.RIGHT }
        val valueClose    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10.5f; textAlign = Paint.Align.LEFT }
        val valueR        = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10.5f; textAlign = Paint.Align.RIGHT }
        val tableHeadTxt  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val tableHeadTxtR = Paint(tableHeadTxt).apply { textAlign = Paint.Align.RIGHT }
        val cellTxt       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10.5f }
        val cellMuted     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 10f }
        val cellMutedR    = Paint(cellMuted).apply { textAlign = Paint.Align.RIGHT }
        val cellBoldR     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkLight; textSize = 10.5f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
        val totalLR       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
        val totalValR     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkLight; textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
        val wordsLabel    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9.5f }
        val wordsVal      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10.5f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val thanks        = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkLight; textSize = 10.5f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val thanksSub     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9f }
        val linePaint     = Paint().apply { color = faint; strokeWidth = 1f }
        val tableBg       = Paint().apply { color = tableHead }
        val footerBg      = Paint().apply { color = Color.rgb(241, 245, 249) }

        val left  = MARGIN
        val right = PAGE_WIDTH - MARGIN
        var y = MARGIN + 20f

        receipt.businessLogoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val logoBmp = BitmapFactory.decodeFile(file.absolutePath)
                logoBmp?.let { canvas.drawBitmap(it, null, RectF(left, y - 22f, left + 34f, y + 12f), null) }
            }
        }
        canvas.drawText(receipt.businessName, left + 44f, y, businessName)

        val badge = badgeLabel(receipt.documentTitle)
        val badgeWidth = badgeText.measureText(badge) + 28f
        canvas.drawRoundRect(RectF(right - badgeWidth, y - 22f, right, y + 6f), 14f, 14f, tableBg)
        canvas.drawText(badge, right - badgeWidth / 2, y - 5f, badgeText)

        y += 30f
        canvas.drawLine(left, y, right, y, linePaint); y += 20f

        val dateOnly = receipt.dateLabel.substringBefore(" ")
        val timeOnly = receipt.dateLabel.substringAfter(" ", "")

        fun drawInfoLineLeft(labelText: String, valueText: String) {
            canvas.drawText("$labelText :", left, y, label)
            val labelWidth = label.measureText("$labelText :")
            canvas.drawText(valueText, left + labelWidth + 8f, y, valueClose)
            y += 17f
        }

        fun drawInfoLineRight(labelText: String, valueText: String) {
            canvas.drawText(valueText, right, y, valueR)
            val valueWidth = valueR.measureText(valueText)
            canvas.drawText("$labelText :", right - valueWidth - 8f, y, labelR)
            y += 17f
        }

        drawInfoLineLeft("N°", referenceNumber(receipt.documentTitle))
        drawInfoLineLeft("Date", dateOnly)
        if (timeOnly.isNotBlank()) drawInfoLineLeft("Heure", timeOnly)
        drawInfoLineLeft(receipt.partyLabel, receipt.partyName)
        y += 10f

        val colN      = left
        val colNom    = left + 30f
        val colNbCol  = left + 220f
        val colUnite  = left + 300f
        val colPU     = right - 110f
        val colTotal  = right

        canvas.drawRoundRect(RectF(left, y - 16f, right, y + 8f), 6f, 6f, tableBg)
        canvas.drawText("N°", colN + 4f, y, tableHeadTxt)
        canvas.drawText("Nom", colNom, y, tableHeadTxt)
        canvas.drawText("Nombre colis", colNbCol, y, tableHeadTxt)
        canvas.drawText("Unité/colis", colUnite, y, tableHeadTxt)
        canvas.drawText("Prix unitaire", colPU, y, tableHeadTxtR)
        canvas.drawText("Total", colTotal, y, tableHeadTxtR)
        y += 26f

        receipt.items.forEachIndexed { index, item ->
            canvas.drawText("${index + 1}", colN + 4f, y, cellMuted)
            canvas.drawText(item.name, colNom, y, cellTxt)
            canvas.drawText(nbColisText(item), colNbCol, y, cellMuted)
            canvas.drawText(unitePerColisText(item), colUnite, y, cellMuted)
            canvas.drawText(formatAmount(item.unitPrice), colPU, y, cellMutedR)
            canvas.drawText(formatAmount(item.totalPrice), colTotal, y, cellBoldR)

            val dividerY = y + 8f
            y += 24f
            if (index < receipt.items.lastIndex) {
                canvas.drawLine(left, dividerY, right, dividerY, linePaint)
            }
        }

        y += 14f
        canvas.drawLine(left, y, right, y, Paint().apply { color = inkLight; strokeWidth = 1.5f; alpha = 90 })
        y += 26f

        // ── TOTAL — محاذاة يمين، متقاربة ──
        canvas.drawText("${formatAmount(receipt.total)} DA", right, y, totalValR)
        val totalValWidth = totalValR.measureText("${formatAmount(receipt.total)} DA")
        canvas.drawText("TOTAL", right - totalValWidth - 12f, y, totalLR)
        y += 22f

        if (receipt.paid > 0 || receipt.balance != receipt.total) {
            drawInfoLineRight("Payé", "${formatAmount(receipt.paid)} DA")
            if (receipt.balance > 0) drawInfoLineRight("Reste", "${formatAmount(receipt.balance)} DA")
            else drawInfoLineRight("Statut", "Réglé")
        }

        y += 8f
        canvas.drawText("Arrêté à la somme de :", left, y, wordsLabel); y += 14f
        canvas.drawText(receipt.amountInWords, left, y, wordsVal); y += 30f

        receipt.note?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Note: $it", left, y, cellMuted); y += 24f
        }

        canvas.drawText("Signature", right - 90f, y, Paint(label).apply { textAlign = Paint.Align.LEFT })
        y += 34f
        canvas.drawLine(right - 90f, y, right, y, linePaint)

        y += 30f

        val footerHeight = 54f
        canvas.drawRoundRect(RectF(left, y, right, y + footerHeight), 10f, 10f, footerBg)
        canvas.drawText("Merci pour votre confiance !", left + 14f, y + 22f, thanks)
        canvas.drawText("À bientôt", left + 14f, y + 38f, thanksSub)

        val qrBitmap = QrCodeGenerator.generate(receipt.qrContent, 120)
        canvas.drawBitmap(qrBitmap, null, RectF(right - 44f, y + 6f, right - 6f, y + 48f), null)

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "receipts").apply { if (!exists()) mkdirs() }
        val safeName = receipt.documentTitle.replace(" ", "_").replace("#", "")
        val file = File(dir, "recu_${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        return file
    }

    fun getShareableUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}