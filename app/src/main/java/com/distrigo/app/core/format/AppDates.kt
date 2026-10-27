package com.distrigo.app.core.format

import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A date as the app's language writes it on a screen: `mardi 27 octobre` in French, `الثلاثاء 27 أكتوبر` in Arabic,
 * digits 0–9 in both (java.time keeps them; WesternDigits makes the default locale ask for them anyway).
 *
 * For screens only. What a date is stored, exported, printed or put in a QR code as keeps its own fixed pattern and
 * locale (D3: documents stay French). Formatters are cached by pattern and language, so a screen recomposing never
 * builds one again; the language is the default locale's, which follows the app's language.
 */
object AppDates {

    private val cache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        cache.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
