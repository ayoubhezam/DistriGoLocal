package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A policy's calendar periods: month, quarter and year, both ends included. */
class CommissionPeriodTest {

    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun `a month runs from the 1st to its last day`() {
        val october = CommissionPeriod.containing(PeriodType.MONTHLY, day("2026-10-17"))
        assertEquals(day("2026-10-01"), october.start)
        assertEquals(day("2026-10-31"), october.end)
        assertEquals(31, october.days)
        assertEquals(29, CommissionPeriod.containing(PeriodType.MONTHLY, day("2028-02-10")).days)
    }

    @Test
    fun `a quarter is three calendar months`() {
        val q3 = CommissionPeriod.containing(PeriodType.QUARTERLY, day("2026-08-22"))
        assertEquals(day("2026-07-01"), q3.start)
        assertEquals(day("2026-09-30"), q3.end)
        assertEquals(day("2026-10-01"), CommissionPeriod.containing(PeriodType.QUARTERLY, day("2026-12-31")).start)
        assertEquals(day("2026-03-31"), CommissionPeriod.containing(PeriodType.QUARTERLY, day("2026-01-01")).end)
    }

    @Test
    fun `a year is the calendar year`() {
        val year = CommissionPeriod.containing(PeriodType.YEARLY, day("2026-10-17"))
        assertEquals(day("2026-01-01"), year.start)
        assertEquals(day("2026-12-31"), year.end)
        assertEquals(365, year.days)
    }

    @Test
    fun `the previous period crosses a year`() {
        assertEquals(day("2026-09-01"), CommissionPeriod.containing(PeriodType.MONTHLY, day("2026-10-17")).previous().start)
        val q4 = CommissionPeriod.containing(PeriodType.QUARTERLY, day("2026-02-01")).previous()
        assertEquals(day("2025-10-01"), q4.start)
        assertEquals(day("2025-12-31"), q4.end)
        assertEquals(day("2025-01-01"), CommissionPeriod.containing(PeriodType.YEARLY, day("2026-06-01")).previous().start)
    }

    @Test
    fun `a period holds its own days only`() {
        val october = CommissionPeriod.containing(PeriodType.MONTHLY, day("2026-10-17"))
        assertTrue(day("2026-10-01") in october)
        assertTrue(day("2026-10-31") in october)
        assertFalse(day("2026-11-01") in october)
        assertFalse(day("2026-09-30") in october)
    }
}
