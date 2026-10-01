package com.distrigo.app.data.print

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.data.print.lang.CanvasReceiptRenderer
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grouped amounts still fit their cells on the real renderer.
 *
 * Grouping makes an amount longer ("12345.00" is "12 345,00"), and the no-break spaces that keep it
 * whole mean StaticLayout cannot wrap it at a group: if it outgrows its cell it breaks mid-number.
 * The layout sizes the figures for a four-figure unit price and a five-figure line total; here each
 * such row is drawn and must be exactly as tall as the same row with one-digit amounts — one line.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptAmountsFitTest {

    private fun receipt(unitPrice: Double, lineTotal: Double, total: Double, format: MoneyFormat) = ReceiptData(
        documentTitle = "Vente N° 27", referenceNumber = "V-6DED-000027", partyLabel = "Client", partyName = "Ahmed",
        dateLabel = "12/09/2026", timeLabel = "14:32",
        items = listOf(ReceiptLineItem("Lait", 10.0, "carton", unitPrice, lineTotal)),
        total = total, paid = 0.0, moneyFormat = format,
    )

    /** The item's own row: the table row on wide paper, the figures row under the name on narrow. */
    private fun itemRow(data: ReceiptData, paper: PaperProfile): ReceiptRow = ThermalLayout.layout(data, paper).first { row ->
        (row is ReceiptRow.Cells && row.cells.first().text == "Lait") ||
            (row is ReceiptRow.Columns && row.left.trimStart().startsWith("10 carton"))
    }

    private fun totalRow(data: ReceiptData, paper: PaperProfile): ReceiptRow =
        ThermalLayout.layout(data, paper).first { it is ReceiptRow.Columns && it.left == "TOTAL" }

    @Test
    fun theWorstRealisticFiguresStayOnOneLine() {
        for (paper in listOf(PaperProfile.MM58, PaperProfile.MM80)) {
            val renderer = CanvasReceiptRenderer(paper)
            for (format in MoneyFormat.entries) {
                val small = receipt(1.0, 1.0, 1.0, format)
                val large = receipt(9999.99, 99999.99, 9_999_999.99, format)
                assertEquals(
                    "$paper $format item row wrapped",
                    renderer.render(itemRow(small, paper))!!.height,
                    renderer.render(itemRow(large, paper))!!.height,
                )
                assertEquals(
                    "$paper $format TOTAL wrapped",
                    renderer.render(totalRow(small, paper))!!.height,
                    renderer.render(totalRow(large, paper))!!.height,
                )
            }
        }
    }
}
