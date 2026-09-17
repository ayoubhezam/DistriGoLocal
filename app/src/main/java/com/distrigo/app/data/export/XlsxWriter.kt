package com.distrigo.app.data.export

import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a real Excel workbook: one sheet per dataset, in one file.
 *
 * ### Why not CSV
 *
 * A `.csv` says nothing about itself. Excel splits it by the list separator of the computer it is opened on,
 * reads a code of digits as a number and drops its leading zeros, and runs a cell beginning with `=` as a
 * formula. A `.xlsx` carries its own structure: a text cell is text — a barcode keeps its zeros and a formula
 * is never run — a number is a number and a date is a date, on any computer, in Excel, LibreOffice or Sheets.
 *
 * ### Why by hand
 *
 * A `.xlsx` is a ZIP of small XML parts, which is well within reach: [ExportCell] maps onto inline strings and
 * numbers, and only a handful of number formats are needed. The libraries that read and write the whole format
 * (Apache POI) weigh tens of megabytes and are a poor fit for a phone.
 *
 * ### How it is written
 *
 * Each sheet is streamed into the ZIP as its rows are read, so a long history is never held in memory. The parts
 * that have to know every sheet — the workbook and its relationships — are written by [close], at the end.
 */
class XlsxWriter(out: OutputStream, private val zone: ZoneId = ZoneId.systemDefault()) : Closeable {

    private val zip = ZipOutputStream(out)
    private val sheetNames = mutableListOf<String>()

    /**
     * Writes [rows] as a sheet named [name], with a frozen header row. Returns the rows written. Sheets appear in
     * the workbook in the order they are written.
     */
    fun <T> sheet(name: String, columns: List<ExportColumn<T>>, rows: Sequence<T>): Int {
        val unique = uniqueSheetName(name)
        sheetNames += unique
        var count = 0
        entry("xl/worksheets/sheet${sheetNames.size}.xml") { writer ->
            writer.write(XML_DECLARATION)
            writer.write("<worksheet xmlns=\"$MAIN_NS\">")
            // The header stays in view while the rows scroll under it.
            writer.write("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            writer.write("<cols>")
            columns.forEachIndexed { index, column ->
                writer.write("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"${width(column.header)}\" customWidth=\"1\"/>")
            }
            writer.write("</cols><sheetData>")

            writer.write("<row r=\"1\">")
            columns.forEachIndexed { index, column ->
                writer.write(cell(reference(index, 1), ExportCell.Text(column.header), zone, HEADER_STYLE))
            }
            writer.write("</row>")

            for (item in rows) {
                count++
                val rowNumber = count + 1
                writer.write("<row r=\"$rowNumber\">")
                columns.forEachIndexed { index, column ->
                    writer.write(cell(reference(index, rowNumber), column.cell(item), zone))
                }
                writer.write("</row>")
            }
            writer.write("</sheetData></worksheet>")
        }
        return count
    }

    /** Writes the parts that describe the workbook, and finishes the file. Does not close the stream underneath. */
    override fun close() {
        entry("[Content_Types].xml") { it.write(contentTypes()) }
        entry("_rels/.rels") { it.write(ROOT_RELATIONSHIPS) }
        entry("xl/workbook.xml") { it.write(workbook()) }
        entry("xl/_rels/workbook.xml.rels") { it.write(workbookRelationships()) }
        entry("xl/styles.xml") { it.write(STYLES) }
        zip.finish()
        zip.flush()
    }

    private fun entry(name: String, write: (Writer) -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        val writer = OutputStreamWriter(KeepOpen(zip), Charsets.UTF_8).buffered()
        write(writer)
        writer.flush()
        zip.closeEntry()
    }

    private fun contentTypes(): String = buildString {
        append(XML_DECLARATION)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sheetNames.indices.forEach {
            append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun workbook(): String = buildString {
        append(XML_DECLARATION)
        append("<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$RELATIONSHIP_NS\"><sheets>")
        sheetNames.forEachIndexed { index, name ->
            append("<sheet name=\"${escape(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelationships(): String = buildString {
        append(XML_DECLARATION)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        sheetNames.indices.forEach {
            append("<Relationship Id=\"rId${it + 1}\" Type=\"$RELATIONSHIP_NS/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetNames.size + 1}\" Type=\"$RELATIONSHIP_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    /** A name Excel accepts: at most 31 characters, none of `[]:*?/\`, and not one already used. */
    private fun uniqueSheetName(name: String): String {
        val cleaned = name.filterNot { it in "[]:*?/\\" }.take(31).ifBlank { "Feuille" }
        if (cleaned !in sheetNames) return cleaned
        var suffix = 2
        while ("${cleaned.take(28)} ($suffix)" in sheetNames) suffix++
        return "${cleaned.take(28)} ($suffix)"
    }

    /** Lets a writer be flushed and closed without closing the ZIP underneath. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    companion object {
        private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        private const val ROOT_RELATIONSHIPS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$RELATIONSHIP_NS/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

        // The styles a cell can use, in the order cellXfs lists them below.
        const val DEFAULT_STYLE = 0
        const val HEADER_STYLE = 1
        const val AMOUNT_STYLE = 2
        const val QUANTITY_STYLE = 3
        const val INTEGER_STYLE = 4
        const val DATE_TIME_STYLE = 5
        const val DATE_STYLE = 6
        const val TEXT_STYLE = 7

        /**
         * Two fonts, the formats the datasets need, and one cell style per use. `#,##0.00` and `dd/mm/yyyy` are
         * written with the separators of whoever opens the file, so an amount reads `3 450,00` in French Excel.
         */
        private val STYLES = XML_DECLARATION +
            "<styleSheet xmlns=\"$MAIN_NS\">" +
            "<numFmts count=\"4\">" +
            "<numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/>" +
            "<numFmt numFmtId=\"165\" formatCode=\"#,##0.###\"/>" +
            "<numFmt numFmtId=\"166\" formatCode=\"dd/mm/yyyy\\ hh:mm\"/>" +
            "<numFmt numFmtId=\"167\" formatCode=\"dd/mm/yyyy\"/>" +
            "</numFmts>" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"8\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

        /** Excel counts days from 30 December 1899; 1 January 1970 is day 25569. */
        private const val EPOCH_OFFSET = 25569L

        /** `A1`, `B2`, `AA3`: the cell at [column] (from 0) of [row] (from 1). */
        fun reference(column: Int, row: Int): String {
            val letters = StringBuilder()
            var rest = column
            while (true) {
                letters.insert(0, 'A' + rest % 26)
                rest = rest / 26 - 1
                if (rest < 0) break
            }
            return "$letters$row"
        }

        /** One cell of a row, or nothing at all when it has no value: an absent cell reads as empty. */
        fun cell(reference: String, cell: ExportCell, zone: ZoneId, style: Int? = null): String {
            fun number(value: String, defaultStyle: Int) = "<c r=\"$reference\" s=\"${style ?: defaultStyle}\"><v>$value</v></c>"
            fun text(value: String, defaultStyle: Int) =
                "<c r=\"$reference\" t=\"inlineStr\" s=\"${style ?: defaultStyle}\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"

            return when (cell) {
                is ExportCell.Text -> cell.value?.takeIf { it.isNotEmpty() }?.let { text(it, TEXT_STYLE) }.orEmpty()
                is ExportCell.Number -> cell.value?.takeIf { !it.isNaN() && !it.isInfinite() }
                    ?.let { number(plain(it, cell.decimals), if (cell.trimZeros) QUANTITY_STYLE else AMOUNT_STYLE) }
                    .orEmpty()
                is ExportCell.Integer -> cell.value?.let { number(it.toString(), INTEGER_STYLE) }.orEmpty()
                is ExportCell.DateTime -> cell.iso?.let { iso ->
                    val moment = try {
                        Instant.parse(iso).atZone(zone).toLocalDateTime()
                    } catch (e: DateTimeParseException) {
                        null
                    }
                    when {
                        moment != null -> number(serial(moment), DATE_TIME_STYLE)
                        // Older rows hold a calendar date where an instant belongs; anything else stays as text.
                        else -> cell(reference, ExportCell.Date(iso), zone, style)
                    }
                }.orEmpty()
                is ExportCell.Date -> cell.iso?.let { iso ->
                    val day = try {
                        LocalDate.parse(iso.take(10))
                    } catch (e: DateTimeParseException) {
                        null
                    }
                    if (day != null) number(serial(day), DATE_STYLE) else text(iso, TEXT_STYLE)
                }.orEmpty()
                is ExportCell.YesNo -> when (cell.value) {
                    true -> text("Oui", TEXT_STYLE)
                    false -> text("Non", TEXT_STYLE)
                    null -> ""
                }
            }
        }

        fun serial(day: LocalDate): String = (day.toEpochDay() + EPOCH_OFFSET).toString()

        fun serial(moment: LocalDateTime): String {
            val days = moment.toLocalDate().toEpochDay() + EPOCH_OFFSET
            val fraction = BigDecimal.valueOf(moment.toLocalTime().toSecondOfDay().toLong())
                .divide(BigDecimal.valueOf(86_400L), 10, RoundingMode.HALF_UP)
            return BigDecimal.valueOf(days).add(fraction).toPlainString()
        }

        /** A number as XML wants it: a plain decimal with a dot, never in scientific notation. */
        fun plain(value: Double, decimals: Int): String =
            BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

        /** How wide a column opens: enough for its header, within reason. */
        fun width(header: String): Int = (header.length + 4).coerceIn(10, 40)

        /** XML text: the five characters that must be escaped, and no control character Excel would refuse. */
        fun escape(value: String): String = buildString(value.length) {
            for (c in value) {
                when {
                    c == '&' -> append("&amp;")
                    c == '<' -> append("&lt;")
                    c == '>' -> append("&gt;")
                    c == '"' -> append("&quot;")
                    c == '\'' -> append("&apos;")
                    c == '\n' || c == '\t' -> append(c)
                    c.code < 0x20 || c.code == 0x7F -> Unit
                    else -> append(c)
                }
            }
        }
    }
}
