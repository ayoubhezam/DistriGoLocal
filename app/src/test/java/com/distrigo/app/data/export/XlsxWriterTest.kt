package com.distrigo.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/** The workbook DistriGo writes: its parts, its cells, and the types Excel will read them as. */
class XlsxWriterTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    private fun workbook(block: XlsxWriter.() -> Unit): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter(out, algiers).use { it.block() }
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    private fun parse(xml: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray())).documentElement

    /** The cells of a row, by their reference: the element, so its type and style can be read. */
    private fun cells(sheet: String, row: Int): Map<String, Element> {
        val rows = parse(sheet).getElementsByTagName("row")
        for (i in 0 until rows.length) {
            val element = rows.item(i) as Element
            if (element.getAttribute("r") != row.toString()) continue
            val cells = element.getElementsByTagName("c")
            return (0 until cells.length).associate { index ->
                val cell = cells.item(index) as Element
                cell.getAttribute("r") to cell
            }
        }
        return emptyMap()
    }

    private fun Element.value(): String = getElementsByTagName("v").item(0).textContent

    private fun Element.text(): String = getElementsByTagName("t").item(0).textContent

    private fun Element.style(): Int = getAttribute("s").toInt()

    private data class Row(val label: String, val amount: Double?, val quantity: Double?, val at: String?)

    private val columns = listOf(
        ExportColumn<Row>("Client") { ExportCell.Text(it.label) },
        ExportColumn("Total (DA)") { ExportCell.Number(it.amount) },
        ExportColumn("Quantité") { ExportCell.Number(it.quantity, decimals = 3, trimZeros = true) },
        ExportColumn("Date") { ExportCell.DateTime(it.at) },
    )

    private fun sheetOf(vararg rows: Row): String =
        workbook { sheet("Ventes", columns, rows.asSequence()) }.getValue("xl/worksheets/sheet1.xml")

    @Test
    fun `a workbook holds the parts Excel expects, one per sheet`() {
        val parts = workbook {
            sheet("Ventes", columns, emptySequence())
            sheet("Clients", columns, emptySequence())
        }
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml",
                "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            parts.keys
        )
        val sheets = parse(parts.getValue("xl/workbook.xml")).getElementsByTagName("sheet")
        assertEquals(2, sheets.length)
        assertEquals("Ventes", (sheets.item(0) as Element).getAttribute("name"))
        assertEquals("Clients", (sheets.item(1) as Element).getAttribute("name"))
        assertEquals("rId2", (sheets.item(1) as Element).getAttribute("r:id"))

        val relationships = parse(parts.getValue("xl/_rels/workbook.xml.rels")).getElementsByTagName("Relationship")
        val targets = (0 until relationships.length).associate {
            val r = relationships.item(it) as Element
            r.getAttribute("Id") to r.getAttribute("Target")
        }
        assertEquals("worksheets/sheet2.xml", targets["rId2"])
        assertEquals("styles.xml", targets["rId3"])
        for (part in parts.values) parse(part) // every part is well-formed XML
    }

    @Test
    fun `the header is bold and stays in view, and columns open wide enough`() {
        val sheet = sheetOf()
        val header = cells(sheet, 1)
        assertEquals(listOf("Client", "Total (DA)", "Quantité", "Date"), listOf("A1", "B1", "C1", "D1").map { header.getValue(it).text() })
        assertTrue(header.values.all { it.style() == XlsxWriter.HEADER_STYLE })
        val pane = parse(sheet).getElementsByTagName("pane").item(0) as Element
        assertEquals("1", pane.getAttribute("ySplit"))
        assertEquals("frozen", pane.getAttribute("state"))
        val columns = parse(sheet).getElementsByTagName("col")
        assertEquals(4, columns.length)
        assertEquals("14", (columns.item(1) as Element).getAttribute("width")) // "Total (DA)" is 10 characters
    }

    @Test
    fun `text stays text, so codes keep their zeros and formulas never run`() {
        val row = cells(sheetOf(Row("=HYPERLINK(\"http://x\")", null, null, null)), 2)
        val cell = row.getValue("A2")
        assertEquals("inlineStr", cell.getAttribute("t"))
        assertEquals("no apostrophe is needed: a text cell is not a formula", "=HYPERLINK(\"http://x\")", cell.text())
        assertEquals(XlsxWriter.TEXT_STYLE, cell.style())

        val barcode = cells(sheetOf(Row("0613000000017", null, null, null)), 2).getValue("A2")
        assertEquals("inlineStr", barcode.getAttribute("t"))
        assertEquals("0613000000017", barcode.text())
    }

    @Test
    fun `numbers are numbers, with the format of what they measure`() {
        val row = cells(sheetOf(Row("x", 3450.0, 12.5, null)), 2)
        assertEquals("3450", row.getValue("B2").value())
        assertEquals(XlsxWriter.AMOUNT_STYLE, row.getValue("B2").style())
        assertEquals("12.5", row.getValue("C2").value())
        assertEquals(XlsxWriter.QUANTITY_STYLE, row.getValue("C2").style())
        assertEquals("no separator, no comma, no exponent: XML wants a plain decimal", "12345678.9", XlsxWriter.plain(12345678.9, 2))
        assertEquals("2.35", XlsxWriter.plain(2.345, 2))

        val integer = XlsxWriter.cell("A1", ExportCell.Integer(12), algiers)
        assertTrue(integer, integer.contains("<v>12</v>") && integer.contains("s=\"${XlsxWriter.INTEGER_STYLE}\""))
    }

    /** Excel stores a moment as days since 30 December 1899; the serial must read back as the local moment. */
    @Test
    fun `dates are real dates in local time`() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Algeria.
        val row = cells(sheetOf(Row("x", null, null, "2026-09-16T23:30:00.643262Z")), 2)
        val cell = row.getValue("D2")
        assertEquals(XlsxWriter.DATE_TIME_STYLE, cell.style())
        assertNull("a date is a number, not text", cell.getAttribute("t").ifEmpty { null })
        val serial = cell.value().toDouble()
        assertEquals(LocalDate.of(2026, 9, 17), LocalDate.ofEpochDay(serial.toLong() - 25569))
        assertEquals(30 * 60L, ((serial - serial.toLong()) * 86_400).roundToLong())

        val day = XlsxWriter.cell("A1", ExportCell.Date("2026-12-31"), algiers)
        val expected = LocalDate.of(2026, 12, 31).toEpochDay() + 25569
        assertTrue(day, day.contains("<v>$expected</v>") && day.contains("s=\"${XlsxWriter.DATE_STYLE}\""))
        assertTrue("a calendar date where an instant belongs is still a date",
            XlsxWriter.cell("A1", ExportCell.DateTime("2026-12-31"), algiers).contains("<v>$expected</v>"))
        assertTrue("what cannot be read stays text",
            XlsxWriter.cell("A1", ExportCell.DateTime("hier"), algiers).contains("inlineStr"))
    }

    @Test
    fun `a cell with no value is left out, and yes or no is a word`() {
        val row = cells(sheetOf(Row("x", null, null, null)), 2)
        assertEquals(setOf("A2"), row.keys)
        assertEquals("", XlsxWriter.cell("A1", ExportCell.Text(""), algiers))
        assertEquals("", XlsxWriter.cell("A1", ExportCell.Number(Double.NaN), algiers))
        assertEquals("", XlsxWriter.cell("A1", ExportCell.YesNo(null), algiers))
        assertTrue(XlsxWriter.cell("A1", ExportCell.YesNo(true), algiers).contains("<t xml:space=\"preserve\">Oui</t>"))
    }

    @Test
    fun `text is escaped, and characters Excel refuses are dropped`() {
        val written = XlsxWriter.cell("A1", ExportCell.Text("Épicerie <El & \"Amel\"> سوق"), algiers)
        assertTrue(written, written.contains("Épicerie &lt;El &amp; &quot;Amel&quot;&gt; سوق"))
        assertFalse("the bell character is gone", written.contains(""))
        assertEquals("Ligne 1\nLigne 2", XlsxWriter.escape("Ligne 1\nLigne 2"))
    }

    @Test
    fun `sheet names are shortened, cleaned and kept apart`() {
        val parts = workbook {
            sheet("Mouvements de stock du dépôt et du camion", columns, emptySequence())
            sheet("Ventes", columns, emptySequence())
            sheet("Ventes", columns, emptySequence())
            sheet("Paiements/clients: 2026", columns, emptySequence())
        }
        val sheets = parse(parts.getValue("xl/workbook.xml")).getElementsByTagName("sheet")
        val names = (0 until sheets.length).map { (sheets.item(it) as Element).getAttribute("name") }
        assertEquals(listOf("Mouvements de stock du dépôt et", "Ventes", "Ventes (2)", "Paiementsclients 2026"), names)
        assertTrue(names.all { it.length <= 31 })
    }

    @Test
    fun `references count columns the way Excel does`() {
        assertEquals("A1", XlsxWriter.reference(0, 1))
        assertEquals("Z3", XlsxWriter.reference(25, 3))
        assertEquals("AA1", XlsxWriter.reference(26, 1))
        assertEquals("AB2", XlsxWriter.reference(27, 2))
        assertEquals("BA1", XlsxWriter.reference(52, 1))
    }

    @Test
    fun `rows are counted and numbered from the row under the header`() {
        val out = ByteArrayOutputStream()
        var count = 0
        XlsxWriter(out, algiers).use { count = it.sheet("Ventes", columns, sequenceOf(Row("a", 1.0, 1.0, null), Row("b", 2.0, 2.0, null))) }
        assertEquals(2, count)
        val parts = workbook { sheet("Ventes", columns, sequenceOf(Row("a", 1.0, 1.0, null), Row("b", 2.0, 2.0, null))) }
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertEquals("b", cells(sheet, 3).getValue("A3").text())
        assertEquals(3, parse(sheet).getElementsByTagName("row").length)
    }
}
