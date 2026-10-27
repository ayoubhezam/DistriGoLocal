package com.distrigo.app.data.model

import com.distrigo.app.core.format.westernNumber
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A sum of money in DA, as it is typed: 2 decimals, read with ',' or '.'.
 *
 * The money counterpart of [Quantity]: same reading rules (the French keyboard's decimal key types
 * ','), a different precision. Showing an amount is not done here but by the business's format —
 * see com.distrigo.app.core.format.MoneyFormatter.
 */
object Amount {

    const val DECIMALS = 2

    fun normalize(value: Double): Double =
        if (!value.isFinite()) value else BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).toDouble()

    /**
     * Half a centime: below it, an account's balance is nothing, as it would show 0,00 DA. A balance is a sum of
     * decimals, so an account settled to the centime can still hold a remainder of a millionth of a dinar; compared
     * with zero, that remainder made a settled client a debtor: « 0,00 DA dû » in red, counted in « Dettes en cours »
     * but not by the « Avec dettes » filter (2026-10-17). Every screen and receipt reads a balance through these three;
     * the reports' queries hold the same half centime.
     */
    const val HALF_CENTIME = 0.005

    /** Owed: half a centime or more. */
    fun owing(balance: Double): Boolean = balance >= HALF_CENTIME

    /** Paid in advance: half a centime or more the other way. */
    fun inAdvance(balance: Double): Boolean = balance <= -HALF_CENTIME

    /** Settled: within half a centime of zero. */
    fun settled(balance: Double): Boolean = !owing(balance) && !inAdvance(balance)

    /**
     * What an amount field keeps of what was typed: digits 0–9 (an Arabic keyboard's ٠–٩ and its `٫` read as theirs,
     * westernNumber) and one '.', a ',' turned into '.', 2 decimals at most.
     */
    fun sanitizeInput(raw: String): String {
        val kept = westernNumber(raw).replace(',', '.').filter { it.isDigit() || it == '.' }
        val dot = kept.indexOf('.')
        val single = if (dot < 0) kept else kept.substring(0, dot + 1) + kept.substring(dot + 1).replace(".", "")
        val d = single.indexOf('.')
        val trimmed = if (d >= 0 && single.length - d - 1 > DECIMALS) single.substring(0, d + 1 + DECIMALS) else single
        if (trimmed.isEmpty()) return ""
        // No leading zeros ("007" is 7), and a lone ".5" is "0.5".
        val integer = trimmed.substringBefore('.')
        return integer.trimStart('0').ifEmpty { "0" } + trimmed.substring(integer.length)
    }

    /**
     * What an amount field holds for a saved [value], as [sanitizeInput] would leave it: digits and one '.', no
     * exponent and no trailing zeros — 1.0E7 is "10000000", 2.50 is "2.5". Double's own text wrote « 1.0E7 » and
     * « 100000.0 » in the commission policy's fields (2026-10-20).
     */
    fun inputText(value: Double): String =
        if (!value.isFinite()) "" else BigDecimal.valueOf(normalize(value)).stripTrailingZeros().toPlainString()

    /** A typed amount, or null: ',' or '.', spaces ignored, an Arabic keyboard's digits read as 0–9. */
    fun parse(text: String): Double? {
        val cleaned = westernNumber(text).trim().filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::normalize)
    }
}
