package com.distrigo.app.core.format

import java.util.Locale

/**
 * Digits are written 0–9 everywhere, whatever the app's language or the phone's (the owner's rule, 2026-10-27):
 * `10`, `12 500,00 DA`, `13:45`, `27/10/2026`, `75 %` — never `١٢ ٥٠٠`.
 *
 * What the app writes itself already is: MoneyFormatter and Quantity build their text from BigDecimal, and java.time
 * keeps 0–9 unless it is told otherwise. What is left follows a locale — String.format, NumberFormat, SimpleDateFormat,
 * the Material date picker's days and header — and under Arabic (Egypt, Saudi Arabia…) a locale writes Arabic-Indic
 * digits. [latin] asks a locale for Latin digits (the `-u-nu-latn` keyword) and changes nothing else: not its language,
 * region or text direction, nor which resources it picks.
 *
 * What is typed is the other half: an Arabic keyboard types ٠–٩, which [westernNumber] and [westernDigits] turn into
 * 0–9 before a field keeps them, so the field shows 0–9 and whatever reads it back can parse it.
 */
object WesternDigits {

    /** [locale] writing Latin digits; [locale] itself when it already does or cannot carry the keyword. */
    fun latin(locale: Locale): Locale =
        if (locale.getUnicodeLocaleType(NUMBERING) == LATIN) locale
        else runCatching { Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword(NUMBERING, LATIN).build() }.getOrDefault(locale)

    /**
     * Latin digits for what formats with the default locale: String.format, NumberFormat, SimpleDateFormat, the date
     * picker's days. Android sets the default again from the system's settings on a configuration change, so this runs
     * at launch, when an activity is created and after a change.
     */
    fun applyToDefaultLocale() {
        val current = Locale.getDefault()
        val latin = latin(current)
        if (latin != current) Locale.setDefault(latin)
    }

    private const val NUMBERING = "nu"
    private const val LATIN = "latn"
}

/** [text] with every decimal digit, of any script, written 0–9: `١٢٣` and `۱۲۳` are `123`. Nothing else changes. */
fun westernDigits(text: String): String {
    if (text.none { it.isNonLatinDigit() }) return text
    return buildString(text.length) {
        for (c in text) append(if (c.isNonLatinDigit()) '0' + Character.digit(c, 10) else c)
    }
}

/**
 * What a number field keeps of what an Arabic keyboard types: its digits 0–9 ([westernDigits]), its decimal mark `٫`
 * a '.', its thousands mark `٬` dropped. The field's own rules — one separator, two decimals — apply after.
 */
fun westernNumber(text: String): String =
    westernDigits(text).replace(ARABIC_DECIMAL_SEPARATOR, '.').replace(ARABIC_THOUSANDS_SEPARATOR, "")

private fun Char.isNonLatinDigit(): Boolean = isDigit() && this !in '0'..'9'

private const val ARABIC_DECIMAL_SEPARATOR = '\u066B'
private const val ARABIC_THOUSANDS_SEPARATOR = "\u066C"
