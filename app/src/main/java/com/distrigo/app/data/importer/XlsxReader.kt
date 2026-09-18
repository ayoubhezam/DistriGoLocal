package com.distrigo.app.data.importer

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import kotlin.math.floor
import kotlin.math.roundToLong

/** One value read from a cell, in the type the workbook gave it. Empty cells are not kept at all. */
sealed class XlsxValue {
    data class Text(val value: String) : XlsxValue()

    /**
     * A number, with the [raw] text the file stored, so a long code such as a barcode is read back digit for digit.
     */
    data class Number(val raw: String) : XlsxValue() {
        val value: Double get() = raw.toDouble()

        /** The number as a person would type it: `12.5`, `6130000000123`, never `6.13E12`. */
        val plain: String get() = BigDecimal(raw).stripTrailingZeros().toPlainString()
    }

    /** A number the workbook formats as a date or a time: Excel stores both as a count of days. */
    data class Date(val moment: LocalDateTime) : XlsxValue()

    data class Bool(val value: Boolean) : XlsxValue()

    /** A formula that failed, such as `#N/A` or `#DIV/0!`. */
    data class Error(val code: String) : XlsxValue()
}

/** A row that has at least one value: its number in the sheet (from 1), and its cells by column (from 0). */
class XlsxRow(val number: Int, val cells: Map<Int, XlsxValue>) {
    operator fun get(column: Int): XlsxValue? = cells[column]
}

class XlsxSheet(val name: String, val rows: List<XlsxRow>)

class XlsxWorkbook(val sheets: List<XlsxSheet>) {
    /** The sheet called [name], ignoring case and surrounding spaces: `Produits` finds ` produits`. */
    fun sheet(name: String): XlsxSheet? = sheets.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
}

/** Why a file could not be read, for the screen to say in its own words. */
class XlsxReadException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason {
        /** Not a ZIP, or a ZIP without a workbook in it. */
        NOT_A_WORKBOOK,

        /** An old `.xls`, or a `.xlsx` protected by a password: both are OLE files, not ZIPs. */
        LEGACY_OR_PROTECTED,

        /** More than an import of a catalogue could need; also what a ZIP bomb looks like. */
        TOO_LARGE,

        /** A workbook whose parts cannot be read. */
        DAMAGED,
    }
}

/**
 * Reads a `.xlsx` workbook: every sheet, as rows of typed values.
 *
 * It is the counterpart of `XlsxWriter`, but it has to read what *Excel* saves, which is more than DistriGo
 * writes: text kept once in a shared table, rich text in runs, formulas with their last result, rows and cells
 * that skip their reference, dates that are only numbers with a date format, and the 1904 calendar of old Mac
 * workbooks.
 *
 * The file is read from a stream, as a picked document comes, so the parts are kept in memory until all have
 * arrived: a ZIP may list them in any order. [MAX_PART_BYTES] and [MAX_TOTAL_BYTES] bound what that can cost,
 * well above any catalogue and well below what a phone can hold. Images and other binary parts are skipped
 * without being read.
 */
object XlsxReader {

    const val MAX_PART_BYTES = 40L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 80L * 1024 * 1024
    const val MAX_ROWS = 100_000

    /** The highest column Excel has: XFD. */
    private const val MAX_COLUMN = 16_383

    fun read(input: InputStream, maxPartBytes: Long = MAX_PART_BYTES, maxTotalBytes: Long = MAX_TOTAL_BYTES): XlsxWorkbook {
        val parts = unzip(input, maxPartBytes, maxTotalBytes)
        val workbookPath = relationships(parts, "_rels/.rels", "")
            .firstOrNull { it.type.endsWith("/officeDocument") }?.target
            ?: "xl/workbook.xml"
        val workbookXml = parts[workbookPath] ?: throw XlsxReadException(XlsxReadException.Reason.NOT_A_WORKBOOK)
        val folder = workbookPath.substringBeforeLast('/', "")
        val links = relationships(parts, "$folder/_rels/${workbookPath.substringAfterLast('/')}.rels", folder)

        val workbook = WorkbookHandler().also { parse(workbookXml, it) }
        val shared = links.firstOrNull { it.type.endsWith("/sharedStrings") }?.target?.let { parts[it] }
            ?.let { xml -> SharedStringsHandler().also { parse(xml, it) }.strings }
            .orEmpty()
        val dateStyles = links.firstOrNull { it.type.endsWith("/styles") }?.target?.let { parts[it] }
            ?.let { xml -> StylesHandler().also { parse(xml, it) }.dateStyles() }
            .orEmpty()

        val sheets = workbook.sheets.mapNotNull { (name, id) ->
            val target = links.firstOrNull { it.id == id && it.type.endsWith("/worksheet") }?.target ?: return@mapNotNull null
            val xml = parts[target] ?: throw XlsxReadException(XlsxReadException.Reason.DAMAGED)
            val handler = SheetHandler(shared, dateStyles, workbook.date1904)
            parse(xml, handler)
            XlsxSheet(name, handler.rows)
        }
        return XlsxWorkbook(sheets)
    }

    // ── The ZIP ──

    private fun unzip(input: InputStream, maxPartBytes: Long, maxTotalBytes: Long): Map<String, ByteArray> {
        val buffered = BufferedInputStream(input)
        buffered.mark(8)
        val magic = ByteArray(4)
        val got = buffered.readNBytesCompat(magic)
        buffered.reset()
        if (got == 4 && magic.contentEquals(OLE_MAGIC)) throw XlsxReadException(XlsxReadException.Reason.LEGACY_OR_PROTECTED)

        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (entry.isDirectory || !(name.endsWith(".xml") || name.endsWith(".rels"))) continue
                    val bytes = readBounded(zip, maxPartBytes)
                    total += bytes.size
                    if (total > maxTotalBytes) throw XlsxReadException(XlsxReadException.Reason.TOO_LARGE)
                    parts[name] = bytes
                }
            }
        } catch (e: XlsxReadException) {
            throw e
        } catch (e: ZipException) {
            throw XlsxReadException(if (parts.isEmpty()) XlsxReadException.Reason.NOT_A_WORKBOOK else XlsxReadException.Reason.DAMAGED, e)
        } catch (e: java.io.EOFException) {
            // The file stops in the middle of a part.
            throw XlsxReadException(XlsxReadException.Reason.DAMAGED, e)
        } catch (e: IllegalArgumentException) {
            // A malformed entry name.
            throw XlsxReadException(XlsxReadException.Reason.DAMAGED, e)
        }
        if (parts.isEmpty()) throw XlsxReadException(XlsxReadException.Reason.NOT_A_WORKBOOK)
        return parts
    }

    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            size += n
            if (size > limit) throw XlsxReadException(XlsxReadException.Reason.TOO_LARGE)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun InputStream.readNBytesCompat(into: ByteArray): Int {
        var read = 0
        while (read < into.size) {
            val n = read(into, read, into.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())

    // ── Relationships: which part is where ──

    private class Link(val id: String, val type: String, val target: String)

    /** The links of a `.rels` part, their targets resolved against [base], the folder of the part they describe. */
    private fun relationships(parts: Map<String, ByteArray>, path: String, base: String): List<Link> {
        val xml = parts[path] ?: return emptyList()
        val links = mutableListOf<Link>()
        parse(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                if (localName != "Relationship" || attributes.value("TargetMode") == "External") return
                val id = attributes.value("Id") ?: return
                val type = attributes.value("Type") ?: return
                val target = attributes.value("Target") ?: return
                links += Link(id, type, resolve(base, target))
            }
        })
        return links
    }

    /** `worksheets/sheet1.xml` from `xl` is `xl/worksheets/sheet1.xml`; a target from `/` is from the root. */
    private fun resolve(base: String, target: String): String {
        val segments = ArrayDeque<String>()
        if (!target.startsWith("/")) base.split('/').filter { it.isNotEmpty() }.forEach { segments.addLast(it) }
        for (segment in target.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    // ── XML ──

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        // A workbook has no business declaring a DTD, and none is ever fetched. Android's parser has no such
        // features and never resolves external entities; the JVM's does both unless told not to.
        for (feature in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) {
            try {
                factory.setFeature(feature.first, feature.second)
            } catch (e: Exception) {
                // Not supported by this parser.
            }
        }
        try {
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.setEntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
            reader.parse(InputSource(ByteArrayInputStream(xml)))
        } catch (e: XlsxReadException) {
            throw e
        } catch (e: SAXException) {
            (e.exception as? XlsxReadException)?.let { throw it }
            throw XlsxReadException(XlsxReadException.Reason.DAMAGED, e)
        } catch (e: IOException) {
            throw XlsxReadException(XlsxReadException.Reason.DAMAGED, e)
        } catch (e: RuntimeException) {
            throw XlsxReadException(XlsxReadException.Reason.DAMAGED, e)
        }
    }

    /** An attribute by its local name, with or without a prefix: `r:id` and `id` alike. */
    private fun Attributes.value(name: String): String? {
        for (i in 0 until length) {
            val local = getLocalName(i).takeUnless { it.isNullOrEmpty() } ?: getQName(i).substringAfter(':')
            if (local == name) return getValue(i)
        }
        return null
    }

    /** The sheets in the order the workbook shows them, with the link to each one's part; and its calendar. */
    private class WorkbookHandler : DefaultHandler() {
        val sheets = mutableListOf<Pair<String, String>>()
        var date1904 = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "sheet" -> {
                    val name = attributes.value("name") ?: return
                    val id = attributes.value("id") ?: return
                    sheets += name to id
                }
                "workbookPr" -> date1904 = attributes.value("date1904").let { it == "1" || it == "true" }
            }
        }
    }

    /** The text Excel keeps once and cells point to by position, joining the runs of rich text. */
    private class SharedStringsHandler : DefaultHandler() {
        val strings = mutableListOf<String>()
        private val current = StringBuilder()
        private var inText = false
        private var phonetic = 0

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "si" -> current.setLength(0)
                "t" -> inText = phonetic == 0
                "rPh" -> phonetic++
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "si" -> strings += current.toString()
                "t" -> inText = false
                "rPh" -> phonetic--
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) current.appendRange(ch, start, start + length)
        }
    }

    /** Which cell styles show a date or a time, from their number format. */
    private class StylesHandler : DefaultHandler() {
        private val formats = mutableMapOf<Int, String>()
        private val cellFormats = mutableListOf<Int>()
        private var inCellXfs = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "numFmt" -> {
                    val id = attributes.value("numFmtId")?.toIntOrNull() ?: return
                    formats[id] = attributes.value("formatCode").orEmpty()
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) cellFormats += attributes.value("numFmtId")?.toIntOrNull() ?: 0
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName == "cellXfs") inCellXfs = false
        }

        fun dateStyles(): Set<Int> = cellFormats.indices.filterTo(mutableSetOf()) { style ->
            val id = cellFormats[style]
            formats[id]?.let(::isDateFormat) ?: (id in BUILT_IN_DATES)
        }
    }

    /** Excel's own date and time formats, which a file names by number only. */
    private val BUILT_IN_DATES: Set<Int> = (14..22).toSet() + (27..36) + (45..47) + (50..58)

    /**
     * Whether a format code shows a date or a time: it has a day, month, year, hour or second in it, once quoted
     * text (`"DA"`), escaped characters (`\ `) and bracketed parts (`[Red]`, `[$-40C]`) are set aside.
     */
    internal fun isDateFormat(code: String): Boolean {
        val section = code.substringBefore(';')
        val kept = StringBuilder()
        var i = 0
        while (i < section.length) {
            when (val c = section[i]) {
                '"' -> { i = section.indexOf('"', i + 1).let { if (it < 0) section.length else it } }
                '\\', '_', '*' -> i++
                '[' -> {
                    val end = section.indexOf(']', i + 1).let { if (it < 0) section.length else it }
                    // [h], [mm], [ss]: elapsed time, which is still a time.
                    if (section.substring(i + 1, end).all { it.lowercaseChar() in "hms" } && end > i + 1) kept.append('h')
                    i = end
                }
                else -> kept.append(c)
            }
            i++
        }
        return kept.any { it.lowercaseChar() in "dmyhs" }
    }

    /** The rows of one sheet. */
    private class SheetHandler(
        private val shared: List<String>,
        private val dateStyles: Set<Int>,
        private val date1904: Boolean,
    ) : DefaultHandler() {
        val rows = mutableListOf<XlsxRow>()

        private var rowNumber = 0
        private var cells = mutableMapOf<Int, XlsxValue>()
        private var column = -1
        private var type: String? = null
        private var style = 0
        private val value = StringBuilder()
        private val inline = StringBuilder()
        private var inValue = false
        private var inInlineText = false
        private var phonetic = 0

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "row" -> {
                    rowNumber = attributes.value("r")?.toIntOrNull() ?: (rowNumber + 1)
                    cells = mutableMapOf()
                    column = -1
                }
                "c" -> {
                    column = attributes.value("r")?.let(::columnOf) ?: (column + 1)
                    if (column > MAX_COLUMN) throw SAXException(XlsxReadException(XlsxReadException.Reason.DAMAGED))
                    type = attributes.value("t")
                    style = attributes.value("s")?.toIntOrNull() ?: 0
                    value.setLength(0)
                    inline.setLength(0)
                }
                "v" -> inValue = true
                "t" -> inInlineText = phonetic == 0
                "rPh" -> phonetic++
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "v" -> inValue = false
                "t" -> inInlineText = false
                "rPh" -> phonetic--
                "c" -> cellValue()?.let { cells[column] = it }
                "row" -> if (cells.isNotEmpty()) {
                    if (rows.size >= MAX_ROWS) throw SAXException(XlsxReadException(XlsxReadException.Reason.TOO_LARGE))
                    rows += XlsxRow(rowNumber, cells)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            when {
                inValue -> value.appendRange(ch, start, start + length)
                inInlineText -> inline.appendRange(ch, start, start + length)
            }
        }

        private fun cellValue(): XlsxValue? {
            val raw = value.toString()
            return when (type) {
                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }?.takeIf { it.isNotEmpty() }?.let { XlsxValue.Text(it) }
                "inlineStr" -> inline.toString().takeIf { it.isNotEmpty() }?.let { XlsxValue.Text(it) }
                // A formula's text result.
                "str" -> raw.takeIf { it.isNotEmpty() }?.let { XlsxValue.Text(it) }
                "b" -> raw.trim().takeIf { it.isNotEmpty() }?.let { XlsxValue.Bool(it == "1" || it.equals("true", true)) }
                "e" -> raw.trim().takeIf { it.isNotEmpty() }?.let { XlsxValue.Error(it) }
                // An ISO date, which few writers use.
                "d" -> raw.trim().takeIf { it.isNotEmpty() }?.let { iso ->
                    runCatching { XlsxValue.Date(LocalDateTime.parse(iso.removeSuffix("Z"))) }.getOrNull()
                        ?: runCatching { XlsxValue.Date(LocalDate.parse(iso.take(10)).atStartOfDay()) }.getOrNull()
                        ?: XlsxValue.Text(iso)
                }
                else -> {
                    val number = raw.trim()
                    when {
                        number.isEmpty() -> null
                        number.toDoubleOrNull()?.isFinite() != true -> XlsxValue.Text(number)
                        style in dateStyles -> moment(number.toDouble(), date1904)?.let { XlsxValue.Date(it) } ?: XlsxValue.Number(number)
                        else -> XlsxValue.Number(number)
                    }
                }
            }
        }
    }

    /** `B7` is column 1. A reference without letters keeps the next column. */
    internal fun columnOf(reference: String): Int? {
        var column = 0
        var letters = 0
        for (c in reference) {
            val upper = c.uppercaseChar()
            if (upper !in 'A'..'Z') break
            column = column * 26 + (upper - 'A' + 1)
            letters++
            if (letters > 3) return null
        }
        return if (letters == 0) null else column - 1
    }

    private val EPOCH_1900: LocalDateTime = LocalDate.of(1899, 12, 30).atStartOfDay()
    private val EPOCH_1904: LocalDateTime = LocalDate.of(1904, 1, 1).atStartOfDay()

    /**
     * The moment a date serial stands for, to the second. Day 0 of the 1900 calendar is 30 December 1899, which
     * absorbs Excel's phantom 29 February 1900 for every date after it.
     */
    internal fun moment(serial: Double, date1904: Boolean): LocalDateTime? {
        if (serial < 0 || serial > 2_958_465) return null // After 31 December 9999.
        val days = floor(serial).toLong()
        val seconds = ((serial - days) * 86_400).roundToLong()
        return (if (date1904) EPOCH_1904 else EPOCH_1900).plusDays(days).plusSeconds(seconds)
    }
}
