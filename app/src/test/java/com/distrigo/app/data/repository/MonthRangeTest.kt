package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The month filter moved from `substr(date_time, 1, 7) = :month` to a half-open range, so an index
 * can serve it. These check the range is the same set of rows the string test matched — including
 * the December rollover and the instants either side of a month boundary. Shared by charges and
 * pertes, which filter their months the same way.
 */
class MonthRangeTest {

    @Test
    fun `a month becomes its first day and the next month's first day`() {
        assertEquals("2026-09-01" to "2026-10-01", monthRange("2026-09"))
        assertEquals("2026-01-01" to "2026-02-01", monthRange("2026-01"))
    }

    @Test
    fun `December rolls over into the next year`() {
        assertEquals("2026-12-01" to "2027-01-01", monthRange("2026-12"))
    }

    @Test
    fun `the range matches exactly what the old substr test matched`() {
        val stamps = listOf(
            "2026-08-31T23:59:59Z",
            "2026-09-01T00:00:00Z",
            "2026-09-01T00:00:00.001Z",
            "2026-09-15T10:04:46Z",
            "2026-09-30T23:59:59.999Z",
            "2026-10-01T00:00:00Z",
            "2026-12-31T23:59:59Z",
            "2027-01-01T00:00:00Z"
        )
        for (month in listOf("2026-08", "2026-09", "2026-10", "2026-12", "2027-01")) {
            val (start, end) = monthRange(month)
            for (stamp in stamps) {
                val oldWay = stamp.take(7) == month
                val newWay = stamp >= start && stamp < end
                assertEquals("$stamp in $month", oldWay, newWay)
            }
        }
    }

    @Test
    fun `every month of a year round-trips`() {
        for (m in 1..12) {
            val month = "2026-%02d".format(m)
            val (start, end) = monthRange(month)
            assertEquals("$month-01", start)
            assertTrue("$start must precede $end", start < end)
            assertEquals(month, start.take(7))
        }
    }
}
