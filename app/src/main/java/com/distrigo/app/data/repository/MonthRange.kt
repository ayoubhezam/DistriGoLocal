package com.distrigo.app.data.repository

/**
 * A month, "yyyy-MM", as the half-open range ["2026-09-01", "2026-10-01").
 *
 * The month used to be tested with `substr(date_time, 1, 7) = :month`, which wraps the column in a
 * function and so could use no index: every row was read and the month computed on each. A range
 * test reads only the rows inside it. The same rows match either way, because `date_time` is
 * ISO-8601 and therefore sorts as text in date order.
 *
 * Shared by the charges and pertes month queries, which both index `(type_id, date_time)`.
 */
internal fun monthRange(month: String): Pair<String, String> {
    val start = java.time.YearMonth.parse(month)
    return "$start-01" to "${start.plusMonths(1)}-01"
}
