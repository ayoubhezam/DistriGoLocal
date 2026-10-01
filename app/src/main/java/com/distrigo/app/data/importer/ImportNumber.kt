package com.distrigo.app.data.importer

/**
 * A number typed as text in a workbook cell, read whatever the separators of whoever typed it.
 *
 * A numeric cell needs none of this: Excel stores the value itself. Text is what a cell holds when it was
 * typed as text, pasted from elsewhere, or came from a CSV — and its separators are those of the computer,
 * or the person, that wrote it. `3 450,00` from French Excel, `3,450.00` from an English one, `3.450,00`
 * from a German one.
 *
 * The previous reading (spaces dropped, ',' turned into '.') read `1,500` and `1.500` as 1.5: a thousand
 * times too small, and silently. So this reading is strict: separators must sit where a thousands
 * separator or a decimal separator can sit, and a cell that could be read two ways is refused rather than
 * guessed at.
 *
 * It does not depend on the app's own display format: a file comes from another phone, another computer,
 * another person.
 */
internal object ImportNumber {

    sealed class Reading {
        data class Value(val value: Double) : Reading()
        object NotANumber : Reading()
        /** `1,500` or `1.500`: a decimal separator or a thousands separator, nothing tells which. */
        object Ambiguous : Reading()
    }

    /** The spaces a thousands separator is typed or pasted with: plain, no-break, narrow no-break, thin. */
    private val SPACES = setOf(' ', ' ', ' ', ' ')

    fun read(typed: String): Reading {
        var body = typed.trim { it.isWhitespace() || it in SPACES }
        val negative = body.startsWith('-')
        if (negative || body.startsWith('+')) body = body.substring(1)
        if (body.isEmpty()) return Reading.NotANumber
        if (!body.all { it.isDigit() || it == ',' || it == '.' || it in SPACES }) return Reading.NotANumber
        if (!body.first().isDigit() && !(body.first() in ",." && body.length > 1)) return Reading.NotANumber

        val spaced = body.any { it in SPACES }
        val commas = body.count { it == ',' }
        val dots = body.count { it == '.' }

        // Which mark, if any, is the decimal one; the other, or spaces, group the thousands.
        val decimal: Char? = when {
            commas > 0 && dots > 0 -> {
                val last = if (body.lastIndexOf(',') > body.lastIndexOf('.')) ',' else '.'
                if (body.count { it == last } > 1 || spaced) return Reading.NotANumber
                last
            }
            commas + dots == 0 -> null
            else -> {
                val mark = if (commas > 0) ',' else '.'
                when {
                    commas + dots > 1 -> null // `1.236.790`: only a thousands separator repeats
                    spaced -> mark // `3 450,00`: spaces already group, the mark is the decimal
                    isAmbiguous(body, mark) -> return Reading.Ambiguous
                    else -> mark
                }
            }
        }

        val integer = if (decimal == null) body else body.substringBefore(decimal)
        val fraction = if (decimal == null) "" else body.substringAfter(decimal)
        if (decimal != null && (fraction.isEmpty() || !fraction.all { it.isDigit() })) return Reading.NotANumber

        val grouping: Char? = when {
            spaced -> ' '
            decimal == null -> if (commas > 0) ',' else if (dots > 0) '.' else null
            else -> if (decimal == ',' && dots > 0) '.' else if (decimal == '.' && commas > 0) ',' else null
        }
        val digits = if (grouping == null) integer else ungroup(integer.map { if (it in SPACES) ' ' else it }.joinToString(""), grouping)
            ?: return Reading.NotANumber
        if (!digits.all { it.isDigit() }) return Reading.NotANumber

        val plain = (if (negative) "-" else "") + digits.ifEmpty { "0" } + (if (fraction.isEmpty()) "" else ".$fraction")
        return plain.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Reading.Value(it) } ?: Reading.NotANumber
    }

    /**
     * A lone mark with exactly three digits after it and one to three before, as in `1,500` or `12.750`:
     * a thousands separator as much as a decimal one. `0,500` is not — no thousands separator follows a
     * lone zero — and neither is `1234,500`, whose integer part is already too long to be a first group.
     */
    private fun isAmbiguous(body: String, mark: Char): Boolean {
        val before = body.substringBefore(mark)
        val after = body.substringAfter(mark)
        return after.length == 3 && before.length in 1..3 && before != "0"
    }

    /** `1 236 790` → `1236790`, or null when the groups are not of three: `12 34` is not a number. */
    private fun ungroup(integer: String, separator: Char): String? {
        val groups = integer.split(separator)
        if (groups.first().length !in 1..3) return null
        if (groups.drop(1).any { it.length != 3 }) return null
        return groups.joinToString("")
    }
}
