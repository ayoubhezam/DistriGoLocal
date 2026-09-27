package com.distrigo.app.data.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A sum of money in DA, as it is typed and shown: 2 decimals, read with ',' or '.', shown with its
 * thousands apart — "3 500", "1 250.50".
 *
 * The money counterpart of [Quantity]: same reading rules (the French keyboard's decimal key types
 * ','), a different precision.
 */
object Amount {

    const val DECIMALS = 2

    fun normalize(value: Double): Double =
        if (!value.isFinite()) value else BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).toDouble()

    /** What an amount field keeps of what was typed: digits and one '.', a ',' turned into '.', 2 decimals at most. */
    fun sanitizeInput(raw: String): String {
        val kept = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val dot = kept.indexOf('.')
        val single = if (dot < 0) kept else kept.substring(0, dot + 1) + kept.substring(dot + 1).replace(".", "")
        val d = single.indexOf('.')
        val trimmed = if (d >= 0 && single.length - d - 1 > DECIMALS) single.substring(0, d + 1 + DECIMALS) else single
        if (trimmed.isEmpty()) return ""
        // No leading zeros ("007" is 7), and a lone ".5" is "0.5".
        val integer = trimmed.substringBefore('.')
        return integer.trimStart('0').ifEmpty { "0" } + trimmed.substring(integer.length)
    }

    /** A typed amount, or null: ',' or '.', spaces ignored. */
    fun parse(text: String): Double? {
        val cleaned = text.trim().filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::normalize)
    }

    /** "3 500", "1 250.5" → "1 250.50": thousands apart, cents only when there are some. */
    fun format(value: Double): String {
        val v = normalize(value)
        val whole = v == Math.rint(v)
        val text = if (whole) BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP).toPlainString()
                   else BigDecimal.valueOf(v).setScale(DECIMALS, RoundingMode.HALF_UP).toPlainString()
        return groupThousands(text)
    }

    /** "3500.5" → "3 500.5": the integer part grouped by three, the rest as typed. */
    fun groupThousands(text: String): String {
        val negative = text.startsWith('-')
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.').let { if (it < 0) body.length else it }
        val integer = body.substring(0, dot)
        val grouped = integer.reversed().chunked(3).joinToString(" ").reversed()
        return (if (negative) "-" else "") + grouped + body.substring(dot)
    }
}
