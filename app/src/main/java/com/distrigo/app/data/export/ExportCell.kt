package com.distrigo.app.data.export

/**
 * One value of an export, in the shape the data has rather than the shape a file wants: each writer decides how
 * to put it in its own format. Only [Text] ever holds what a user typed.
 */
sealed class ExportCell {
    data class Text(val value: String?) : ExportCell()

    /** A decimal number with [decimals] places, such as an amount (2) or a quantity (3, trailing zeros dropped). */
    data class Number(val value: Double?, val decimals: Int = 2, val trimZeros: Boolean = false) : ExportCell()

    data class Integer(val value: Long?) : ExportCell()

    /** A stored instant, ISO-8601 text in UTC, written in local time. */
    data class DateTime(val iso: String?) : ExportCell()

    /** A stored calendar date, `yyyy-MM-dd`, already local. */
    data class Date(val iso: String?) : ExportCell()

    data class YesNo(val value: Boolean?) : ExportCell()
}

/** A column of a dataset: its header, and how a row fills it. */
class ExportColumn<T>(val header: String, val cell: (T) -> ExportCell)
