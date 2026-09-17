package com.distrigo.app.data.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Days and months in the business's time zone, for values stored in UTC.
 *
 * The rules are in docs/data/timestamps.md. Instants are stored in UTC; the day a sale belongs to is a
 * day in the phone's zone. Taking the first ten characters of an instant gave its UTC day instead, so
 * in Algeria (UTC+1) everything recorded between 00:00 and 01:00 landed on the previous day — a sale at
 * 00:30 read "Hier", and a charge at 00:30 on the 1st counted in the previous month.
 *
 * [zone] defaults to the phone's; the parameter is there so tests can name one.
 */
object BusinessDates {

    private val BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    /**
     * The local day a stored value belongs to, as `yyyy-MM-dd`.
     *
     * An ISO instant (`2026-09-16T23:30:00Z`) is converted through [zone]. Anything else — a calendar
     * date such as a bon's `date`, or a malformed value — keeps its first ten characters, exactly as
     * before, since a calendar date is already local. Null gives "".
     */
    fun localDay(value: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (value == null) return ""
        if (value.length > 10 && value[10] == 'T') {
            runCatching { return Instant.parse(value).atZone(zone).toLocalDate().toString() }
        }
        return value.take(10)
    }

    /**
     * The instant [day] starts in [zone], written to be compared with stored instants as text:
     * `2026-09-30T23:00:00`, UTC, without the `Z`.
     *
     * Without the suffix it sorts just below every stored instant of that same second, whatever its
     * fractional digits — `…23:00:00Z` and `…23:00:00.5Z` both compare greater — so `>=` includes the
     * first moment of the day and `<` excludes it, with no fraction lost at the boundary.
     */
    fun dayStart(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String =
        BOUND.format(day.atStartOfDay(zone).toInstant())

    /** A local month, `yyyy-MM`, as the half-open range of instants it covers: `[start, end)`. */
    fun monthBounds(month: String, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
        val first = YearMonth.parse(month).atDay(1)
        return dayStart(first, zone) to dayStart(first.plusMonths(1), zone)
    }

    /**
     * Local days from [from] to [to], both included, as instant bounds: `[start, end)`, the end being
     * the start of the day after [to]. A null or unreadable day leaves that side open.
     */
    fun dayRangeBounds(from: String?, to: String?, zone: ZoneId = ZoneId.systemDefault()): Pair<String?, String?> {
        fun parse(day: String?) = day?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return parse(from)?.let { dayStart(it, zone) } to parse(to)?.let { dayStart(it.plusDays(1), zone) }
    }
}
