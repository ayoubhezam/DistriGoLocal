package com.distrigo.app.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * The one rule for quantities: how precise they are, how they are read, compared and shown.
 *
 * ### Precision: 3 decimals
 *
 * A quantity is a whole number of thousandths — 1.250 kg, 0.5 carton — stored as a `Double`. The
 * database keeps `REAL` columns and the ledger sums them (StockLedger.kt), so without a rule 0.1 + 0.2
 * would leave a stock of 0.30000000000000004 and "stock ≤ 0" would stop meaning what it says. Every
 * quantity is therefore [normalize]d where it is typed and again where it is written, the ledger rounds
 * its sums to the same 3 decimals, and comparisons allow [EPSILON], half a thousandth, for anything that
 * slipped through. 3 decimals is also what the Excel import has always kept.
 *
 * ### Pièce products count whole units
 *
 * A carton product may move by fractions (half a box). A pièce product may not: nobody sells half a
 * bottle. [allowsFractions] is that rule; forms step and parse by it, repositories refuse what breaks it.
 *
 * ### One way to show a quantity
 *
 * [format]: up to 3 decimals, trailing zeros dropped, '.' as the separator — "2", "0.5", "1.25", "1.255".
 * It replaced a dozen copies of the same function, which all showed two decimals ("0.50").
 */
object Quantity {

    const val DECIMALS = 3

    /** Half of the smallest step: below this, two quantities are the same quantity. */
    const val EPSILON = 0.0005

    /** [value] rounded to 3 decimals, half up. Not finite stays as it is, for the caller to refuse. */
    fun normalize(value: Double): Double {
        if (!value.isFinite()) return value
        val rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).toDouble()
        return if (rounded == 0.0) 0.0 else rounded   // no "-0"
    }

    /**
     * A typed quantity, or null if it is not one. Accepts ',' or '.' as the decimal separator — the French
     * keyboard's decimal key types ',' — and ignores spaces, including the narrow no-break space French
     * number formatting puts between thousands.
     */
    fun parse(text: String): Double? {
        val cleaned = text.trim().filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::normalize)
    }

    /**
     * What a quantity field keeps of what was typed: digits and one separator, a ',' turned into '.'.
     * Without fractions ([allowFractions] false: a pièce product), digits only.
     */
    fun sanitizeInput(raw: String, allowFractions: Boolean = true): String {
        val kept = raw.replace(',', '.').filter { it.isDigit() || (allowFractions && it == '.') }
        val firstDot = kept.indexOf('.')
        val single = if (firstDot < 0) kept else kept.substring(0, firstDot + 1) + kept.substring(firstDot + 1).replace(".", "")
        // No more than 3 decimals can be typed.
        val dot = single.indexOf('.')
        return if (dot >= 0 && single.length - dot - 1 > DECIMALS) single.substring(0, dot + 1 + DECIMALS) else single
    }

    /** "2", "0.5", "1.25", "-3": up to 3 decimals, no trailing zeros, '.' as the separator. */
    fun format(value: Double): String {
        if (!value.isFinite()) return value.toString()
        return BigDecimal.valueOf(normalize(value)).stripTrailingZeros().toPlainString()
    }

    fun isZero(value: Double): Boolean = abs(value) < EPSILON

    fun isWhole(value: Double): Boolean = abs(value - Math.rint(value)) < EPSILON

    /** True when [value] is more than [limit], beyond rounding. */
    fun exceeds(value: Double, limit: Double): Boolean = value > limit + EPSILON

    /** True when [value] is less than [limit], beyond rounding. */
    fun isBelow(value: Double, limit: Double): Boolean = value < limit - EPSILON

    /** A carton product moves by fractions; a pièce product by whole units only. */
    fun allowsFractions(unitType: String?): Boolean = unitType != UNIT_PIECE

    /** True when [value] is a quantity a product of [unitType] can move. */
    fun fitsUnit(value: Double, unitType: String?): Boolean = allowsFractions(unitType) || isWhole(value)

    const val UNIT_PIECE = "pièce"
}
