package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.Cell
import com.distrigo.app.data.print.lang.MonoRaster
import com.distrigo.app.data.print.lang.ReceiptRow
import com.distrigo.app.data.print.lang.RowAlign
import com.distrigo.app.data.print.lang.ThermalLayout
import com.distrigo.app.ui.components.ReceiptData
import com.distrigo.app.ui.components.ReceiptLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What goes on the receipt, and in what order.
 *
 * That is all the layout decides now. Since the receipt is drawn on a Canvas so Arabic can be shaped
 * and reordered, wrapping and measuring belong to the renderer — which needs a real Android
 * `StaticLayout` and is therefore verified on a device rather than here.
 *
 * So these pin structure: that the narrow roll takes two rows per item and the wide one a table,
 * that a row's cells account for the whole width, that nothing wastes paper between items, and that
 * money keeps its centimes.
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

    private fun lines(rows: List<ReceiptRow>) = rows.filterIsInstance<ReceiptRow.Line>().map { it.text }

    private fun cellRows(rows: List<ReceiptRow>) = rows.filterIsInstance<ReceiptRow.Cells>()

    @Test
    fun `every cell row accounts for the whole width`() {
        // The renderer hands the last cell whatever is left over, so fractions that do not sum to one
        // do not overflow — they silently shift a column, which is worse, because it looks deliberate.
        listOf(PaperProfile.MM58, PaperProfile.MM80).forEach { paper ->
            cellRows(ThermalLayout.layout(receipt(), paper)).forEach { row ->
                val sum = row.cells.sumOf { it.fraction.toDouble() }
                assertEquals("cells sum to $sum on ${paper.size}", 1.0, sum, 0.001)
            }
        }
    }

    @Test
    fun `wide paper gets a table and narrow paper gets two rows per item`() {
        val data = receipt(items = listOf(line("Café", 2.0, "kg", 340.0, 680.0)))

        // 72 mm of printable width: name, quantity, unit price and total share one row under a header.
        val wide = ThermalLayout.layout(data, PaperProfile.MM80)
        assertTrue(
            "no table header on 80 mm",
            cellRows(wide).any { row -> row.cells.any { it.text == "Article" } },
        )
        assertTrue(
            "the item is not on one row",
            cellRows(wide).any { row -> row.cells.map(Cell::text).let { "Café" in it && "680.00" in it } },
        )

        // 48 mm leaves about 21 mm for a name once the figures have taken theirs — four characters at
        // a legible size — so the name gets its own row and the figures follow underneath.
        val narrow = ThermalLayout.layout(data, PaperProfile.MM58)
        assertTrue(
            "58 mm should not rule a table",
            cellRows(narrow).none { row -> row.cells.any { it.text == "Article" } },
        )
        assertTrue("name not on its own row", "Café" in lines(narrow))
        assertTrue(
            "total not pinned to the far edge",
            narrow.filterIsInstance<ReceiptRow.Columns>().any { it.right == "680.00" },
        )
    }

    @Test
    fun `product names are left-aligned whatever the script`() {
        // A column of names is read by running an eye down its left edge. Left to itself, Android
        // places a strong-RTL string flush right, so an Arabic entry would start where a French one
        // ends and the column would have no edge to read down. RowAlign.Left overrides the placement
        // without touching the shaping.
        val data = receipt(items = listOf(
            line("Café", 2.0, "kg", 340.0, 680.0),
            line("بطاطا محلية", 30.0, "kg", 80.0, 2400.0),
        ))

        val wide = cellRows(ThermalLayout.layout(data, PaperProfile.MM80))
            .flatMap { it.cells }
            .filter { it.text == "Café" || it.text == "بطاطا محلية" || it.text == "Article" }
        assertTrue("the table's name column is not forced left", wide.isNotEmpty())
        wide.forEach { assertEquals("${it.text} is not left-aligned", RowAlign.Left, it.align) }

        // The narrow roll puts the name on its own row; same rule applies.
        ThermalLayout.layout(data, PaperProfile.MM58)
            .filterIsInstance<ReceiptRow.Line>()
            .filter { it.text == "Café" || it.text == "بطاطا محلية" }
            .also { assertEquals("both names should be their own rows", 2, it.size) }
            .forEach { assertEquals("${it.text} is not left-aligned", RowAlign.Left, it.align) }
    }

    @Test
    fun `items are not separated by blank rows`() {
        val data = receipt(items = listOf(
            line("Café", 2.0, "kg", 340.0, 680.0),
            line("Sucre", 3.0, "kg", 120.0, 360.0),
        ))
        listOf(PaperProfile.MM58, PaperProfile.MM80).forEach { paper ->
            val rows = ThermalLayout.layout(data, paper)
            val first = rows.indexOfFirst { it.mentions("Café") }
            val second = rows.indexOfFirst { it.mentions("Sucre") }
            assertTrue("items not found on ${paper.size}", first >= 0 && second > first)
            // Every dot of feed between them is paper nobody asked for.
            assertTrue(
                "a blank row sits between two items on ${paper.size}",
                rows.subList(first, second).none { it is ReceiptRow.Blank },
            )
        }
    }

    @Test
    fun `the logo is the first thing printed, and only when there is one`() {
        val plain = ThermalLayout.layout(receipt(), PaperProfile.MM58)
        assertTrue("a raster was emitted with no logo", plain.none { it is ReceiptRow.Raster })

        val withLogo = ThermalLayout.layout(
            receipt(), PaperProfile.MM58, MonoRaster(8, 8, ByteArray(8)),
        )
        assertTrue("the logo must print above everything", withLogo.first() is ReceiptRow.Raster)
    }

    @Test
    fun `a signature space appears only when something is still owed`() {
        fun hasSignature(paid: Double) = lines(
            ThermalLayout.layout(receipt(total = 1140.0, paid = paid), PaperProfile.MM58)
        ).any { it.startsWith("Signature") }

        assertTrue("a credit sale needs a signature", hasSignature(500.0))
        assertTrue("a settled sale does not", !hasSignature(1140.0))
    }

    @Test
    fun `the only blank row is the one that clears the tear bar`() {
        // A settled receipt, so the signature's own spacing is not in play.
        val rows = ThermalLayout.layout(receipt(), PaperProfile.MM80)
        val blanks = rows.filterIsInstance<ReceiptRow.Blank>()
        assertEquals("blank rows elsewhere in the receipt", 1, blanks.size)
        // ~15 mm at 8 dots per millimetre.
        assertEquals(120, blanks.single().dots)
    }

    @Test
    fun `money always keeps its centimes`() {
        // The character-grid renderer used to drop decimals when a number outgrew its column. A cell
        // that measures its own contents has no such cliff, and money that quietly loses precision to
        // fit is worse than money that wraps.
        assertEquals("15230.00", ThermalLayout.money(15230.0))
        assertEquals("2109521.08", ThermalLayout.money(2109521.08))
        assertEquals("0.50", ThermalLayout.money(0.5))
    }

    @Test
    fun `a purchase order is badged as one`() {
        val purchase = receipt().copy(documentTitle = "Bon d'achat N° 12")
        assertTrue("BON D'ACHAT" in lines(ThermalLayout.layout(purchase, PaperProfile.MM58)))
        assertTrue("REÇU DE VENTE" in lines(ThermalLayout.layout(receipt(), PaperProfile.MM58)))
    }

    private fun ReceiptRow.mentions(text: String): Boolean = when (this) {
        is ReceiptRow.Line    -> this.text.contains(text)
        is ReceiptRow.Columns -> left.contains(text) || right.contains(text)
        is ReceiptRow.Cells   -> cells.any { it.text.contains(text) }
        else -> false
    }
}
