package com.distrigo.app.data.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Days and months read in Algeria (UTC+1, no daylight saving), for instants stored in UTC. */
class BusinessDatesTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `an instant after local midnight belongs to the local day`() {
        // 00:30 on the 17th in Algeria is 23:30 on the 16th in UTC.
        assertEquals("2026-09-17", BusinessDates.localDay("2026-09-16T23:30:00Z", algiers))
        assertEquals("2026-09-17", BusinessDates.localDay("2026-09-16T23:30:00.643262Z", algiers))
        assertEquals("2026-09-16", BusinessDates.localDay("2026-09-16T22:59:59.999Z", algiers))
        assertEquals("2026-09-16", BusinessDates.localDay("2026-09-16T23:30:00Z", ZoneOffset.UTC))
    }

    @Test
    fun `a calendar date or an unreadable value keeps its first ten characters`() {
        assertEquals("2026-09-16", BusinessDates.localDay("2026-09-16", algiers))
        assertEquals("2026-01-05", BusinessDates.localDay("2026-01-05 08:00", algiers))
        assertEquals("2026-9-1", BusinessDates.localDay("2026-9-1", algiers))
        assertEquals("", BusinessDates.localDay(null, algiers))
    }

    @Test
    fun `a local month starts and ends at local midnight`() {
        assertEquals("2026-09-30T23:00:00" to "2026-10-31T23:00:00", BusinessDates.monthBounds("2026-10", algiers))
        assertEquals("2026-11-30T23:00:00" to "2026-12-31T23:00:00", BusinessDates.monthBounds("2026-12", algiers))
        assertEquals("2026-10-01T00:00:00" to "2026-11-01T00:00:00", BusinessDates.monthBounds("2026-10", ZoneOffset.UTC))
    }

    /** The bounds compare as text with stored instants exactly as the instants compare in time. */
    @Test
    fun `stored instants fall in the right local month whatever their fraction`() {
        val cases = mapOf(
            "2026-09-30T22:59:59.999Z" to "2026-09",
            "2026-09-30T23:00:00Z" to "2026-10",          // 00:00 on 1 October
            "2026-09-30T23:00:00.000001Z" to "2026-10",
            "2026-09-30T23:30:00Z" to "2026-10",          // the charge that used to count in September
            "2026-10-31T22:59:59.5Z" to "2026-10",
            "2026-10-31T23:00:00Z" to "2026-11",
        )
        for (month in listOf("2026-09", "2026-10", "2026-11")) {
            val (start, end) = BusinessDates.monthBounds(month, algiers)
            for ((stamp, expected) in cases) {
                assertEquals("$stamp in $month", expected == month, stamp >= start && stamp < end)
            }
        }
    }

    @Test
    fun `a range of local days includes the whole last day`() {
        val (start, end) = BusinessDates.dayRangeBounds("2026-09-13", "2026-09-13", algiers)
        assertEquals("2026-09-12T23:00:00", start)
        assertEquals("2026-09-13T23:00:00", end)
        for (stamp in listOf("2026-09-12T23:00:00Z", "2026-09-13T10:00:00Z", "2026-09-13T22:59:59.9Z")) {
            assertTrue(stamp, stamp >= start!! && stamp < end!!)
        }
        assertTrue("2026-09-13T23:00:00Z" >= end!!)
        assertTrue("2026-09-12T22:59:59Z" < start!!)
    }

    @Test
    fun `an open or unreadable end of a day range stays open`() {
        assertEquals(null to "2026-09-13T23:00:00", BusinessDates.dayRangeBounds(null, "2026-09-13", algiers))
        assertEquals("2026-09-12T23:00:00" to null, BusinessDates.dayRangeBounds("2026-09-13", "", algiers))
        assertEquals(null to null, BusinessDates.dayRangeBounds("9999", "0", algiers))
    }

    @Test
    fun `a day start is the local midnight in UTC`() {
        assertEquals("2026-03-28T23:00:00", BusinessDates.dayStart(LocalDate.of(2026, 3, 29), algiers))
    }
}
