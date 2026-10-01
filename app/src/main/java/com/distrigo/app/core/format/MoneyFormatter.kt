package com.distrigo.app.core.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Writes an amount the way the business chose to: always to the centime, thousands grouped by [format].
 *
 * Built by hand rather than with `DecimalFormat` or a `Locale`, for two reasons. The result must not
 * depend on the phone's language — two reps' phones must print the same receipt. And Android's French
 * locale groups with a narrow no-break space (U+202F), which a thermal printer's code page cannot hold.
 *
 * Two flavours, differing only in the spaces they write:
 * - [screen]: no-break spaces, so `1 236 790,50 DA` never breaks across two lines of a Compose `Text`;
 * - [printer]: plain ASCII spaces, which every printer code page holds.
 *
 * Instances are shared per format and flavour, so a changed setting is a changed reference and an
 * unchanged one is not.
 *
 * Display only: never seed an editable field, a draft, an export or a QR code with what this writes.
 */
class MoneyFormatter private constructor(val format: MoneyFormat, private val space: Char) {

    /** `1 236 790,50`: two decimals always, half-up, never `-0,00`. */
    fun amount(value: Double): String {
        if (!value.isFinite()) return NOT_A_NUMBER
        val rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP)
        val sign = if (rounded.signum() < 0) "-" else ""
        return sign + unsigned(rounded.abs())
    }

    /** `1 236 790,50 DA` */
    fun da(value: Double): String = amount(value) + space + CURRENCY

    /** `+1 250,00 DA`, `-1 250,00 DA`, and `0,00 DA` with no sign — for a balance's movement. */
    fun signedDa(value: Double): String {
        if (!value.isFinite()) return NOT_A_NUMBER + space + CURRENCY
        val rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP)
        val sign = when (rounded.signum()) { 1 -> "+"; -1 -> "-"; else -> "" }
        return sign + unsigned(rounded.abs()) + space + CURRENCY
    }

    private fun unsigned(rounded: BigDecimal): String {
        val plain = rounded.toPlainString() // "1236790.50": scale 2, so a '.' is always there
        val integer = plain.substringBefore('.')
        val cents = plain.substringAfter('.')
        val thousands = if (format.thousands == ' ') space else format.thousands
        val grouped = buildString(integer.length + integer.length / 3) {
            integer.forEachIndexed { i, digit ->
                if (i > 0 && (integer.length - i) % 3 == 0) append(thousands)
                append(digit)
            }
        }
        return grouped + format.decimal + cents
    }

    override fun toString() = "MoneyFormatter($format, ${if (space == NO_BREAK_SPACE) "screen" else "printer"})"

    companion object {
        const val DECIMALS = 2
        const val CURRENCY = "DA"

        /** What a value that is not a number shows: never "NaN" on a receipt. */
        const val NOT_A_NUMBER = "—"

        const val NO_BREAK_SPACE = ' '

        private val screens = MoneyFormat.entries.associateWith { MoneyFormatter(it, NO_BREAK_SPACE) }
        private val printers = MoneyFormat.entries.associateWith { MoneyFormatter(it, ' ') }

        /** For the screens: no-break spaces. */
        fun screen(format: MoneyFormat): MoneyFormatter = screens.getValue(format)

        /** For printers and plain text: ASCII spaces only. */
        fun printer(format: MoneyFormat): MoneyFormatter = printers.getValue(format)
    }
}
