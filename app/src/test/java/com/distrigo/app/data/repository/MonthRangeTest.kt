package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The charges and pertes month filter: a half-open range an index can serve, whose bounds are the local
 * month's first moments — see BusinessDates.monthBounds, which BusinessDatesTest covers at the edges.
 */
class MonthRangeTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `a month runs from its first local moment to the next month's`() {
        assertEquals("2026-08-31T23:00:00" to "2026-09-30T23:00:00", monthRange("2026-09", algiers))
    }

    @Test
    fun `December rolls over into the next year`() {
        assertEquals("2026-11-30T23:00:00" to "2026-12-31T23:00:00", monthRange("2026-12", algiers))
    }

    /** In UTC the range still selects exactly the rows the old `substr(date_time, 1, 7)` test did. */
    @Test
    fun `in UTC the range matches what the old substr test matched`() {
        val stamps = listOf(
            "2026-08-31T23:59:59Z", "2026-09-01T00:00:00Z", "2026-09-01T00:00:00.001Z", "2026-09-15T10:04:46Z",
            "2026-09-30T23:59:59.999Z", "2026-10-01T00:00:00Z", "2026-12-31T23:59:59Z", "2027-01-01T00:00:00Z"
        )
        for (month in listOf("2026-08", "2026-09", "2026-10", "2026-12", "2027-01")) {
            val (start, end) = monthRange(month, ZoneOffset.UTC)
            for (stamp in stamps) {
                assertEquals("$stamp in $month", stamp.take(7) == month, stamp >= start && stamp < end)
            }
        }
    }

    @Test
    fun `every month of a year follows on from the last`() {
        var previousEnd: String? = null
        for (m in 1..12) {
            val (start, end) = monthRange(YearMonth.of(2026, m).toString(), algiers)
            assertTrue("$start must precede $end", start < end)
            previousEnd?.let { assertEquals("month $m starts where the last ended", it, start) }
            previousEnd = end
        }
    }
}
