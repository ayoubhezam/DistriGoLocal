package com.distrigo.app.data.model.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Each period's days, as of Friday 2 October 2026, and the bounds they become in Algeria (UTC+1). */
class ReportFilterTest {

    private val today = LocalDate.parse("2026-10-02")
    private val algiers = ZoneId.of("Africa/Algiers")

    private fun days(period: ReportPeriod, from: String? = null, to: String? = null) =
        ReportFilter(period, customFrom = from?.let(LocalDate::parse), customTo = to?.let(LocalDate::parse))
            .days(today).let { "${it.start}..${it.endInclusive}" }

    @Test
    fun `each preset covers its days, today included`() {
        assertEquals("2026-10-02..2026-10-02", days(ReportPeriod.AUJOURDHUI))
        assertEquals("2026-10-01..2026-10-01", days(ReportPeriod.HIER))
        assertEquals("2026-09-26..2026-10-02", days(ReportPeriod.SEPT_JOURS))
        assertEquals("2026-10-01..2026-10-02", days(ReportPeriod.CE_MOIS))
        assertEquals("2026-09-01..2026-09-30", days(ReportPeriod.MOIS_DERNIER))
        assertEquals("2026-01-01..2026-10-02", days(ReportPeriod.CETTE_ANNEE))
    }

    @Test
    fun `last month ends on its own last day`() {
        val march = LocalDate.parse("2026-03-31")
        assertEquals(LocalDate.parse("2026-02-28"), ReportFilter(ReportPeriod.MOIS_DERNIER).days(march).endInclusive)
    }

    @Test
    fun `a custom period takes its days in either order, and one day when only the start is picked`() {
        assertEquals("2026-08-10..2026-08-20", days(ReportPeriod.PERSONNALISE, "2026-08-10", "2026-08-20"))
        assertEquals("2026-08-10..2026-08-20", days(ReportPeriod.PERSONNALISE, "2026-08-20", "2026-08-10"))
        assertEquals("2026-08-10..2026-08-10", days(ReportPeriod.PERSONNALISE, "2026-08-10"))
        assertEquals("2026-10-02..2026-10-02", days(ReportPeriod.PERSONNALISE))
    }

    @Test
    fun `the range runs from the first local midnight to the one after the last day`() {
        val range = ReportFilter(ReportPeriod.CE_MOIS, ReportSource.CAMION).resolve(today, algiers)
        assertEquals("2026-09-30T23:00:00", range.start)
        assertEquals("2026-10-02T23:00:00", range.end)
        assertEquals(LocalDate.parse("2026-10-01"), range.firstDay)
        assertEquals(LocalDate.parse("2026-10-02"), range.lastDay)
        assertEquals("camion", range.source)
        assertNull(ReportFilter(source = ReportSource.TOUT).resolve(today, algiers).source)
    }
}
