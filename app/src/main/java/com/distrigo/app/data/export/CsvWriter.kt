package com.distrigo.app.data.export

import java.io.Closeable
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One value in a CSV row. Its type decides how it is written, and only [Text] can carry a formula. */
sealed class CsvCell {
    data class Text(val value: String?) : CsvCell()

    /** A decimal number with [decimals] places, such as an amount (2) or a quantity (3, trailing zeros dropped). */
    data class Number(val value: Double?, val decimals: Int = 2, val trimZeros: Boolean = false) : CsvCell()

    data class Integer(val value: Long?) : CsvCell()

    /** A stored instant, ISO-8601 text in UTC, written in local time. */
    data class DateTime(val iso: String?) : CsvCell()

    /** A stored calendar date, `yyyy-MM-dd`, already local. */
    data class Date(val iso: String?) : CsvCell()

    data class YesNo(val value: Boolean?) : CsvCell()
}

/** A column of a dataset: its header, and how a row fills it. */
class CsvColumn<T>(val header: String, val cell: (T) -> CsvCell)

/**
 * CSV that French Excel opens by double-click with every character, column and number where it belongs.
 *
 * ### The format
 *
 * - **UTF-8 with a byte-order mark.** Without it Excel reads the file in the Windows code page, and every accent
 *   and Arabic name comes out garbled.
 * - **`;` between cells, CRLF between rows**, under a first line reading `sep=;`. French Excel splits on `;`, since
 *   `,` is its decimal mark - but a double-clicked CSV is split by the separator of *that computer's* Windows
 *   region, `,` on an English one, which puts every row in a single column. The `sep=;` line is Excel's own way
 *   to be told otherwise, whatever the computer. Tools other than Excel may show it as a first row; importing the
 *   file rather than opening it ignores it.
 * - **Decimal commas and no thousands separator**: `1234,50`, which Excel reads as a number. Rounded half up from
 *   the decimal value, never in scientific notation.
 * - **Local dates**: `17/09/2026 14:30` for instants, `17/09/2026` for calendar dates.
 * - **Quoting** only where needed: a cell holding `;`, `"`, a line break, or leading or trailing spaces is quoted,
 *   its quotes doubled.
 *
 * ### Formulas
 *
 * A text cell starting with `=`, `+`, `-` or `@` — or a tab or carriage return, which Excel skips before looking —
 * would be run as a formula when the file is opened: a client named `=HYPERLINK(…)` could send whoever opens the
 * export to a web page. Such a cell is written with a leading `'`, which makes it plain text. Numbers and dates
 * are written by this class, never from user text, so a negative amount stays a number.
 *
 * What CSV cannot do: a code of digits such as a barcode or `0555…` phone number is still read by Excel as a
 * number, losing leading zeros, unless the file is imported with that column set to text.
 */
class CsvWriter(out: OutputStream, private val zone: ZoneId = ZoneId.systemDefault()) : Closeable {

    private val writer: Writer = OutputStreamWriter(out, Charsets.UTF_8).buffered()

    init {
        writer.write(BOM)
        writer.write(SEPARATOR_HINT)
        writer.write(LINE_END)
    }

    fun header(vararg names: String) = row(names.map { CsvCell.Text(it) })

    fun row(vararg cells: CsvCell) = row(cells.asList())

    fun row(cells: List<CsvCell>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) writer.write(SEPARATOR.code)
            writer.write(format(cell, zone))
        }
        writer.write(LINE_END)
    }

    /** Writes [columns]' headers, then one row per item of [rows]. Returns how many rows were written. */
    fun <T> table(columns: List<CsvColumn<T>>, rows: Sequence<T>): Int {
        header(*columns.map { it.header }.toTypedArray())
        var count = 0
        for (item in rows) {
            row(columns.map { it.cell(item) })
            count++
        }
        return count
    }

    fun flush() = writer.flush()

    override fun close() = writer.close()

    companion object {
        const val BOM = "﻿"
        /** Excel's instruction, on the first line, to split on `;` whatever the computer's region settings. */
        const val SEPARATOR_HINT = "sep=;"
        const val SEPARATOR = ';'
        const val LINE_END = "\r\n"

        private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        /** Characters that make Excel read a cell as a formula when they come first. */
        private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

        /** The text a cell is written as, quoted and neutralized as needed. */
        fun format(cell: CsvCell, zone: ZoneId): String = when (cell) {
            is CsvCell.Text -> text(cell.value)
            is CsvCell.Number -> number(cell.value, cell.decimals, cell.trimZeros)
            is CsvCell.Integer -> cell.value?.toString().orEmpty()
            is CsvCell.DateTime -> cell.iso?.let { iso ->
                try {
                    DATE_TIME.format(Instant.parse(iso).atZone(zone))
                } catch (e: DateTimeParseException) {
                    // Older rows hold a calendar date where an instant belongs: still a date.
                    format(CsvCell.Date(iso), zone)
                }
            }.orEmpty()
            is CsvCell.Date -> cell.iso?.let { iso ->
                try {
                    DATE.format(LocalDate.parse(iso.take(10)))
                } catch (e: DateTimeParseException) {
                    text(iso)
                }
            }.orEmpty()
            is CsvCell.YesNo -> when (cell.value) {
                true -> "Oui"
                false -> "Non"
                null -> ""
            }
        }

        /** User text: neutralized if it would start a formula, then quoted if it needs to be. */
        fun text(value: String?): String {
            if (value.isNullOrEmpty()) return ""
            val safe = if (value.first() in FORMULA_STARTS) "'$value" else value
            val needsQuotes = safe.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' } ||
                safe.first().isWhitespace() || safe.last().isWhitespace()
            return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }

        fun number(value: Double?, decimals: Int, trimZeros: Boolean = false): String {
            if (value == null || value.isNaN() || value.isInfinite()) return ""
            var decimal = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
            if (trimZeros) decimal = decimal.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
            if (decimal.signum() == 0) decimal = decimal.abs() // no "-0,00"
            return decimal.toPlainString().replace('.', ',')
        }
    }
}
