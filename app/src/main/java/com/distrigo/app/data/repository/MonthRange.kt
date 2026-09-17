package com.distrigo.app.data.repository

import com.distrigo.app.data.time.BusinessDates
import java.time.ZoneId

/**
 * A local month, "yyyy-MM", as the half-open range of stored instants it covers — see
 * BusinessDates.monthBounds.
 *
 * The month used to be tested with `substr(date_time, 1, 7) = :month`, which wraps the column in a
 * function and so could use no index; a range test reads only the rows inside it. The range used to be
 * ["2026-09-01", "2026-10-01"), UTC midnights, so a charge at 00:30 on the 1st in Algeria — 23:30 UTC the
 * day before — counted in the previous month. Its bounds are now the local month's first moments.
 *
 * Shared by the charges and pertes month queries, which both index `(type_id, date_time)`.
 */
internal fun monthRange(month: String, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> =
    BusinessDates.monthBounds(month, zone)
