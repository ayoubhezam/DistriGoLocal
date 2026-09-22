package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout engine, checked at its two real widths.
 *
 * Phase 0's promise is that the receipt can be got right on a desk with no printer, so these pin the
 * invariants that a renderer or the preview would otherwise have to be trusted with: **no row is ever
 * wider than the paper**, and the money columns keep their thousands when they run out of room.
 */
class ThermalLayoutTest {

    private fun receipt(
        items: List<ReceiptLineItem> = listOf(line("Lait Candia 1L", 12.0, "pièce", 95.0, 1140.0)),
        total: Double = 1140.0,
        paid : Double = 1140.0,
        note : String? = null,
    ) = ReceiptData(
        documentTitle   = "Vente N° 27",
        referenceNumber = "V-6DED-000027",
        partyLabel      = "Client",
        partyName       = "Ahmed Benali",
        dateLabel       = "vendredi 12/09/2026",
        timeLabel       = "14:32",
        items           = items,
        total           = total,
        paid            = paid,
        note            = note,
        businessName    = "DISTRIGO",
        businessPhone   = "0555 12 34 56",
        performedBy     = "Youcef",
        clientType      = "Détail",
        clientSecteur   = "Alger-Centre",
    )

    private fun line(name: String, qty: Double, unit: String, pu: Double, total: Double) =
        ReceiptLineItem(name = name, quantity = qty, unitLabel = unit, unitPrice = pu, totalPrice = total)

    /** Every rendered character of a row, as the printer would see it on one line. */
    private fun printedWidths(rows: List<ReceiptRow>, width: Int): List<Pair<String, Int>> =
        rows.mapNotNull { row ->
            when (row) {
                is ReceiptRow.Line    -> row.text to row.text.length
                is ReceiptRow.Columns -> (row.left + row.right) to (row.left.length + row.right.length + 1)
                is ReceiptRow.Rule    -> row.char.toString() to width
                else -> null
            }
        }

    @Test
    fun `no row overflows 58mm paper`() {
        val rows = ThermalLayout.layout(receipt(), PaperProfile.MM58)
        printedWidths(rows, PaperProfile.MM58.charsPerLine).forEach { (text, w) ->
            assertTrue("\"$text\" is $w wide, paper is 32", w <= 32)
        }
    }

    @Test
    fun `no row overflows 80mm paper`() {
        val rows = ThermalLayout.layout(receipt(), PaperProfile.MM80)
        printedWidths(rows, PaperProfile.MM80.charsPerLine).forEach { (text, w) ->
            assertTrue("\"$text\" is $w wide, paper is 48", w <= 48)
        }
    }

    @Test
    fun `long names and large amounts still fit both widths`() {
        val data = receipt(
            items = listOf(
                line("Lait Candia demi-écrémé longue conservation 1 litre", 24.0, "pièce", 12345.67, 296296.08),
                line("Huile", 1250.5, "kg", 1450.0, 1813225.0),
            ),
            total = 2109521.08,
            paid  = 500000.0,
            note  = "Livraison prévue jeudi matin, entrée par la rue arrière du dépôt principal.",
        )
        listOf(PaperProfile.MM58 to 32, PaperProfile.MM80 to 48).forEach { (paper, width) ->
            printedWidths(ThermalLayout.layout(data, paper), width).forEach { (text, w) ->
                assertTrue("[$width] \"$text\" is $w wide", w <= width)
            }
        }
    }

    @Test
    fun `80mm gets a single-line table and 58mm gets two lines per item`() {
        val data = receipt(items = listOf(line("Café", 2.0, "kg", 340.0, 680.0)))

        val wide = ThermalLayout.layout(data, PaperProfile.MM80).filterIsInstance<ReceiptRow.Line>()
        // The item's name, quantity, unit price and total all land on one line, under a header.
        assertTrue("no table header", wide.any { it.text.startsWith("Article") })
        assertTrue("item not on one line", wide.any { it.text.contains("Café") && it.text.contains("680.00") })

        val narrow = ThermalLayout.layout(data, PaperProfile.MM58)
        assertTrue("58mm should not print a table header",
            narrow.filterIsInstance<ReceiptRow.Line>().none { it.text.startsWith("Article") })
        // Name on its own Line, figures on a Columns row with the total pinned right.
        assertTrue("name not on its own line",
            narrow.filterIsInstance<ReceiptRow.Line>().any { it.text == "Café" })
        assertTrue("total not pinned right",
            narrow.filterIsInstance<ReceiptRow.Columns>().any { it.right.trim() == "680.00" })
    }

    @Test
    fun `a signature line appears only when something is still owed`() {
        fun hasSignature(paid: Double) = ThermalLayout
            .layout(receipt(total = 1140.0, paid = paid), PaperProfile.MM58)
            .filterIsInstance<ReceiptRow.Line>()
            .any { it.text.startsWith("Signature") }

        assertTrue("a credit sale needs a signature", hasSignature(500.0))
        assertTrue("a settled sale does not", !hasSignature(1140.0))
    }

    @Test
    fun `money drops centimes before it drops thousands`() {
        assertEquals("15230.00", ThermalLayout.money(15230.0, 9))
        // Too narrow for two decimals: rounds rather than cutting a leading digit, because a receipt
        // reading 15230 is right to the dinar and one reading 230.00 is a dispute.
        assertEquals("15230", ThermalLayout.money(15230.0, 6))
        assertEquals("15230", ThermalLayout.money(15230.4, 5))
    }

    @Test
    fun `wrap never exceeds the width and breaks words that cannot fit`() {
        val wrapped = ThermalLayout.wrap("Lait Candia demi-écrémé longue conservation", 12)
        wrapped.forEach { assertTrue("\"$it\" exceeds 12", it.length <= 12) }
        assertEquals("Lait Candia", wrapped.first())

        val unbreakable = ThermalLayout.wrap("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 10)
        unbreakable.forEach { assertTrue("\"$it\" exceeds 10", it.length <= 10) }
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", unbreakable.joinToString("").replace(" ", ""))
    }

    @Test
    fun `the QR is a whole number of bytes wide and no wider than half the paper`() {
        listOf(PaperProfile.MM58, PaperProfile.MM80).forEach { paper ->
            val size = ThermalLayout.qrSizeDots(paper)
            assertEquals("not byte-aligned on ${paper.size}", 0, size % 8)
            assertTrue("QR wider than half the paper on ${paper.size}", size <= paper.dotsPerLine / 2)
        }
    }
}
