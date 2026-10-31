package com.distrigo.app.core.format

import com.distrigo.app.core.locale.AppLanguage
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

    /**
     * A numeric date (`dd/MM/yyyy`, `dd/MM`) read day first in either language (the owner, 2026-10-29). Its digits and
     * slashes would otherwise join into one left-to-right number, the year on the right of an Arabic line; in Arabic a
     * right-to-left mark after each slash keeps the parts apart, so the day is on the right and the line reads day,
     * month, year. French is the pattern as given. Screens only: what is stored or exported keeps its plain pattern.
     */
    fun numeric(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        formatter(if (AppLanguage.of(locale) == AppLanguage.ARABIC) pattern.replace("/", "/$RLM") else pattern, locale)

    /** A stored ISO day (`2026-10-29`) as a screen shows it: as is in French, `29/10/2026` read day first in Arabic. */
    fun shownIsoDay(isoDay: String, locale: Locale = Locale.getDefault()): String =
        if (AppLanguage.of(locale) != AppLanguage.ARABIC) isoDay
        else runCatching { java.time.LocalDate.parse(isoDay).format(numeric("dd/MM/yyyy", locale)) }.getOrDefault(isoDay)

    /** « jeudi, 29 octobre 2026 », « الخميس، 29 أكتوبر 2026 »: the day in full, with the language's own comma. */
    fun fullDay(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        formatter(if (AppLanguage.of(locale) == AppLanguage.ARABIC) "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy", locale)

    private val RLM = Char(0x200F)
}
