package com.distrigo.app.data.importer

import com.distrigo.app.data.export.ExportCell
import com.distrigo.app.data.export.ExportColumn
import com.distrigo.app.data.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reading workbooks: DistriGo's own, one saved by another program, and the parts Excel writes its own way. */
class XlsxReaderTest {

    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun zip(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = XlsxReader.read(ByteArrayInputStream(bytes))

    private fun failure(bytes: ByteArray, maxPart: Long = XlsxReader.MAX_PART_BYTES, maxTotal: Long = XlsxReader.MAX_TOTAL_BYTES): XlsxReadException.Reason {
        try {
            XlsxReader.read(ByteArrayInputStream(bytes), maxPart, maxTotal)
        } catch (e: XlsxReadException) {
            return e.reason
        }
        fail("read should have failed")
        throw AssertionError()
    }

    /** A workbook the way Excel saves one, with [sheetXml] as its only sheet, `Feuil1`. */
    private fun excel(
        sheetXml: String,
        shared: List<String> = emptyList(),
        styles: String? = null,
        workbookPr: String = "",
        sharedXml: String? = null,
    ): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            "_rels/.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            // Excel writes absolute targets in some versions: /xl/worksheets/sheet1.xml.
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId3\" Type=\"$rel/worksheet\" Target=\"/xl/worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId4\" Type=\"$rel/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
                "<Relationship Id=\"rId5\" Type=\"$rel/styles\" Target=\"styles.xml\"/></Relationships>",
            "xl/workbook.xml" to "<workbook xmlns=\"$main\" xmlns:r=\"$rel\">$workbookPr<sheets><sheet name=\"Feuil1\" sheetId=\"1\" r:id=\"rId3\"/></sheets></workbook>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"$main\"><sheetData>$sheetXml</sheetData></worksheet>",
        )
        parts["xl/sharedStrings.xml"] = sharedXml
            ?: ("<sst xmlns=\"$main\" count=\"${shared.size}\">" + shared.joinToString("") { "<si><t xml:space=\"preserve\">$it</t></si>" } + "</sst>")
        styles?.let { parts["xl/styles.xml"] = "<styleSheet xmlns=\"$main\">$it</styleSheet>" }
        return zip(parts)
    }

    private fun only(bytes: ByteArray) = read(bytes).sheets.single()

    // ── DistriGo's own workbook ──

    @Test
    fun readsBackWhatTheWriterWrote() {
        data class Line(val name: String, val code: String, val price: Double, val qty: Double, val day: String, val at: String)
        val out = ByteArrayOutputStream()
        XlsxWriter(out, ZoneId.of("Africa/Algiers")).use {
            it.sheet(
                "Produits",
                listOf(
                    ExportColumn<Line>("Nom") { l -> ExportCell.Text(l.name) },
                    ExportColumn("Code-barres") { l -> ExportCell.Text(l.code) },
                    ExportColumn("Prix") { l -> ExportCell.Number(l.price) },
                    ExportColumn("Quantité") { l -> ExportCell.Number(l.qty, 3, trimZeros = true) },
                    ExportColumn("Péremption") { l -> ExportCell.Date(l.day) },
                    ExportColumn("Créé") { l -> ExportCell.DateTime(l.at) },
                ),
                sequenceOf(Line("حليب & <lait>", "0613000000123", 3450.0, 12.5, "2026-12-31", "2026-09-17T23:30:15Z")),
            )
            it.sheet("Clients", listOf(ExportColumn<String>("Nom") { n -> ExportCell.Text(n) }), sequenceOf("Amine"))
        }
        val workbook = read(out.toByteArray())

        assertEquals(listOf("Produits", "Clients"), workbook.sheets.map { it.name })
        val (header, line) = workbook.sheet("produits")!!.rows
        assertEquals(1, header.number)
        assertEquals(XlsxValue.Text("Péremption"), header[4])
        assertEquals(2, line.number)
        assertEquals(XlsxValue.Text("حليب & <lait>"), line[0])
        assertEquals(XlsxValue.Text("0613000000123"), line[1])
        assertEquals("3450", (line[2] as XlsxValue.Number).plain)
        assertEquals(12.5, (line[3] as XlsxValue.Number).value, 0.0)
        assertEquals(XlsxValue.Date(LocalDate.of(2026, 12, 31).atStartOfDay()), line[4])
        // Written in Algiers time, one hour ahead of UTC.
        assertEquals(XlsxValue.Date(LocalDateTime.of(2026, 9, 18, 0, 30, 15)), line[5])
        assertEquals(XlsxValue.Text("Amine"), workbook.sheet("Clients")!!.rows[1][0])
    }

    // ── A workbook saved by another program ──

    @Test
    fun readsAWorkbookSavedByOpenpyxl() {
        val bytes = javaClass.getResourceAsStream("/xlsx/openpyxl-produits.xlsx")!!.readBytes()
        val workbook = read(bytes)
        val rows = workbook.sheet("Produits")!!.rows

        // The empty fourth row is not there, and the others keep their own numbers.
        assertEquals(listOf(1, 2, 3, 5), rows.map { it.number })
        assertEquals(XlsxValue.Text("Date de péremption"), rows[0][4])

        val milk = rows[1]
        assertEquals(XlsxValue.Text("حليب كانديا 1L"), milk[0])
        assertEquals(XlsxValue.Text("0613000000123"), milk[1])
        // "#,##0.00 \"DA\"" has a d in it, but only inside quotes: still an amount.
        assertEquals(XlsxValue.Number("120.5"), milk[2])
        assertEquals("12", (milk[3] as XlsxValue.Number).plain)
        assertEquals(XlsxValue.Date(LocalDate.of(2026, 12, 31).atStartOfDay()), milk[4])
        assertEquals(XlsxValue.Bool(true), milk[5])

        val coffee = rows[2]
        // A barcode typed as a number comes back digit for digit.
        assertEquals("6130000000123", (coffee[1] as XlsxValue.Number).plain)
        assertNull(coffee[3])
        assertEquals(XlsxValue.Date(LocalDateTime.of(2027, 3, 1, 14, 30)), coffee[4])
        assertEquals(XlsxValue.Bool(false), coffee[5])

        val sugar = rows[3]
        assertEquals(XlsxValue.Text("  Sucre  "), sugar[0])
        // A formula never calculated has no value to read.
        assertNull(sugar[2])

        val clients = workbook.sheet("clients")!!.rows
        assertNull(clients[0][1])
        assertEquals(XlsxValue.Text("Téléphone"), clients[0][2])
    }

    // ── What Excel writes its own way ──

    @Test
    fun readsSharedStringsRichTextAndPhoneticGuides() {
        val sharedXml = "<sst xmlns=\"$main\">" +
            "<si><t>Nom</t></si>" +
            "<si><r><rPr><b/></rPr><t>Café </t></r><r><t>Bonal</t></r></si>" +
            "<si><t>東京</t><rPh sb=\"0\" eb=\"2\"><t>トウキョウ</t></rPh></si>" +
            "<si><t/></si>" +
            "</sst>"
        val sheet = only(excel(
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c>" +
                "<c r=\"D1\" t=\"s\"><v>3</v></c><c r=\"E1\" t=\"s\"><v>99</v></c></row>",
            sharedXml = sharedXml,
        ))
        val row = sheet.rows.single()
        assertEquals(XlsxValue.Text("Nom"), row[0])
        assertEquals(XlsxValue.Text("Café Bonal"), row[1])
        assertEquals(XlsxValue.Text("東京"), row[2])
        // An empty string, and a pointer past the end of the table, are empty cells.
        assertNull(row[3])
        assertNull(row[4])
    }

    @Test
    fun placesRowsAndCellsThatSkipTheirReference() {
        val sheet = only(excel(
            "<row><c><v>1</v></c><c><v>2</v></c><c r=\"E1\"><v>5</v></c><c><v>6</v></c></row>" +
                "<row r=\"4\"><c r=\"AA4\"><v>27</v></c></row><row><c><v>1</v></c></row>"
        ))
        val (first, fourth, fifth) = sheet.rows
        assertEquals(1, first.number)
        assertEquals(mapOf(0 to "1", 1 to "2", 4 to "5", 5 to "6"), first.cells.mapValues { (it.value as XlsxValue.Number).raw })
        assertEquals(4, fourth.number)
        assertEquals(XlsxValue.Number("27"), fourth[26])
        assertEquals(5, fifth.number)
    }

    @Test
    fun readsFormulaResultsBooleansAndErrors() {
        val row = only(excel(
            "<row r=\"1\">" +
                "<c r=\"A1\" t=\"str\"><f>CONCAT(\"a\",\"b\")</f><v>ab</v></c>" +
                "<c r=\"B1\"><f>1+1</f><v>2</v></c>" +
                "<c r=\"C1\" t=\"b\"><v>1</v></c>" +
                "<c r=\"D1\" t=\"e\"><v>#N/A</v></c>" +
                "<c r=\"E1\" t=\"inlineStr\"><is><r><t>en </t></r><r><t>ligne</t></r></is></c>" +
                "<c r=\"F1\" s=\"0\"/>" +
                "<c r=\"G1\"><v>1.5E-3</v></c>" +
                "</row>"
        )).rows.single()
        assertEquals(XlsxValue.Text("ab"), row[0])
        assertEquals(XlsxValue.Number("2"), row[1])
        assertEquals(XlsxValue.Bool(true), row[2])
        assertEquals(XlsxValue.Error("#N/A"), row[3])
        assertEquals(XlsxValue.Text("en ligne"), row[4])
        assertNull(row[5])
        assertEquals("0.0015", (row[6] as XlsxValue.Number).plain)
    }

    @Test
    fun readsDatesByTheirStyleBuiltInOrCustom() {
        // Styles: 0 General, 1 built-in 14 (short date), 2 custom date, 3 custom amount in DA, 4 elapsed hours.
        val styles = "<numFmts count=\"3\">" +
            "<numFmt numFmtId=\"164\" formatCode=\"[$-40C]dd\\ mmmm\\ yyyy\"/>" +
            "<numFmt numFmtId=\"165\" formatCode=\"#,##0.00\\ &quot;DA&quot;;[Red]\\-#,##0.00\\ &quot;DA&quot;\"/>" +
            "<numFmt numFmtId=\"166\" formatCode=\"[h]:mm\"/>" +
            "</numFmts>" +
            // cellStyleXfs must not be mistaken for the cell styles.
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"14\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/><xf numFmtId=\"164\"/><xf numFmtId=\"165\"/><xf numFmtId=\"166\"/></cellXfs>"
        val row = only(excel(
            "<row r=\"1\"><c r=\"A1\"><v>46022</v></c><c r=\"B1\" s=\"1\"><v>46022</v></c><c r=\"C1\" s=\"2\"><v>46022.75</v></c>" +
                "<c r=\"D1\" s=\"3\"><v>46022</v></c><c r=\"E1\" s=\"4\"><v>0.5</v></c></row>",
            styles = styles,
        )).rows.single()
        assertEquals(XlsxValue.Number("46022"), row[0])
        assertEquals(XlsxValue.Date(LocalDate.of(2025, 12, 31).atStartOfDay()), row[1])
        assertEquals(XlsxValue.Date(LocalDateTime.of(2025, 12, 31, 18, 0)), row[2])
        assertEquals(XlsxValue.Number("46022"), row[3])
        assertEquals(XlsxValue.Date(LocalDateTime.of(1899, 12, 30, 12, 0)), row[4])
    }

    @Test
    fun readsTheMacCalendar() {
        val row = only(excel(
            "<row r=\"1\"><c r=\"A1\" s=\"1\"><v>44560</v></c></row>",
            styles = "<cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/></cellXfs>",
            workbookPr = "<workbookPr date1904=\"1\"/>",
        )).rows.single()
        // The 1904 calendar counts 1462 days fewer for the same date.
        assertEquals(XlsxValue.Date(LocalDate.of(2025, 12, 31).atStartOfDay()), row[0])
    }

    @Test
    fun dateFormatsAreToldApartFromNumberFormats() {
        listOf("dd/mm/yyyy", "yyyy-mm-dd hh:mm", "h:mm AM/PM", "[$-40C]d mmmm", "[h]:mm:ss", "mmm-yy").forEach {
            assertTrue(it, XlsxReader.isDateFormat(it))
        }
        listOf("General", "0.00", "#,##0.00 \"DA\"", "0.00E+00", "[Red]0.00", "0%", "@", "#,##0_);(#,##0)").forEach {
            assertFalse(it, XlsxReader.isDateFormat(it))
        }
    }

    @Test
    fun readsColumnLetters() {
        assertEquals(0, XlsxReader.columnOf("A1"))
        assertEquals(25, XlsxReader.columnOf("Z9"))
        assertEquals(26, XlsxReader.columnOf("AA3"))
        assertEquals(16_383, XlsxReader.columnOf("XFD1"))
        assertNull(XlsxReader.columnOf("12"))
        assertNull(XlsxReader.columnOf("ABCD1"))
    }

    @Test
    fun readsSheetsInTheWorkbookOrderNotTheZipOrder() {
        val parts = linkedMapOf(
            "xl/worksheets/sheet2.xml" to "<worksheet xmlns=\"$main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>deux</t></is></c></row></sheetData></worksheet>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"$main\"><sheetData/></worksheet>",
            "xl/workbook.xml" to "<workbook xmlns=\"$main\" xmlns:r=\"$rel\"><sheets>" +
                "<sheet name=\"Première\" sheetId=\"1\" r:id=\"rId2\"/><sheet name=\"Seconde\" sheetId=\"2\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
            // No _rels/.rels at all: the workbook is looked for where it always is.
        )
        val workbook = read(zip(parts))
        assertEquals(listOf("Première", "Seconde"), workbook.sheets.map { it.name })
        assertTrue(workbook.sheets[0].rows.isEmpty())
        assertEquals(XlsxValue.Text("deux"), workbook.sheets[1].rows.single()[0])
    }

    // ── Files that are not workbooks, or not safe to read ──

    @Test
    fun refusesWhatIsNotAWorkbook() {
        assertEquals(XlsxReadException.Reason.NOT_A_WORKBOOK, failure("Nom;Prix\nLait;120\n".toByteArray()))
        assertEquals(XlsxReadException.Reason.NOT_A_WORKBOOK, failure(ByteArray(0)))
        // A ZIP, but of something else: a DistriGo backup, say.
        assertEquals(XlsxReadException.Reason.NOT_A_WORKBOOK, failure(zip(mapOf("manifest.json" to "{}"))))
    }

    @Test
    fun namesOldExcelFilesAndPasswordProtectedOnes() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) + ByteArray(504)
        assertEquals(XlsxReadException.Reason.LEGACY_OR_PROTECTED, failure(ole))
    }

    @Test
    fun refusesDamagedParts() {
        assertEquals(XlsxReadException.Reason.DAMAGED, failure(excel("<row r=\"1\"><c r=\"A1\"><v>1</v></row>")))
        // A sheet the workbook names but the file does not have.
        val bytes = excel("").let { good ->
            val parts = mutableMapOf<String, String>()
            java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { zip ->
                while (true) { val e = zip.nextEntry ?: break; parts[e.name] = zip.readBytes().toString(Charsets.UTF_8) }
            }
            zip(parts - "xl/worksheets/sheet1.xml")
        }
        assertEquals(XlsxReadException.Reason.DAMAGED, failure(bytes))
        // Cut short in the middle.
        val whole = excel("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>")
        val cut = whole.copyOf(whole.size / 2)
        assertTrue(failure(cut) in setOf(XlsxReadException.Reason.DAMAGED, XlsxReadException.Reason.NOT_A_WORKBOOK))
    }

    @Test
    fun refusesADoctypeInsteadOfExpandingIt() {
        val bomb = "<?xml version=\"1.0\"?><!DOCTYPE sst [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>" +
            "<sst xmlns=\"$main\"><si><t>&b;</t></si></sst>"
        assertEquals(XlsxReadException.Reason.DAMAGED, failure(excel("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>", sharedXml = bomb)))
    }

    @Test
    fun refusesPartsBeyondTheLimits() {
        val big = excel("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>${"x".repeat(5_000)}</t></is></c></row>")
        assertEquals(XlsxReadException.Reason.TOO_LARGE, failure(big, maxPart = 4_000))
        assertEquals(XlsxReadException.Reason.TOO_LARGE, failure(big, maxTotal = 4_000))
        // Within the limits, the same file reads.
        assertEquals(5_000, ((read(big).sheets.single().rows.single()[0]) as XlsxValue.Text).value.length)
    }

    @Test
    fun skipsImagesWithoutReadingThem() {
        val good = excel("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>")
        val parts = mutableListOf<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(good)).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; parts += e.name to zip.readBytes() }
        }
        parts += "xl/media/image1.png" to ByteArray(10_000)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> parts.forEach { (n, b) -> zip.putNextEntry(ZipEntry(n)); zip.write(b); zip.closeEntry() } }
        // 10 000 bytes of image would pass neither limit.
        assertEquals(XlsxValue.Number("1"), XlsxReader.read(ByteArrayInputStream(out.toByteArray()), 5_000, 5_000).sheets.single().rows.single()[0])
    }
}
