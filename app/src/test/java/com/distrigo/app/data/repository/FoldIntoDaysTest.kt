package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.SalesHour
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** UTC hours gathered into Algerian days (UTC+1). */
class FoldIntoDaysTest {

    private val algiers = ZoneId.of("Africa/Algiers")
    private val range = ReportFilter(ReportPeriod.SEPT_JOURS).resolve(LocalDate.parse("2026-10-02"), algiers)

    @Test
    fun `an hour after local midnight counts on the local day, and every day is present`() {
        val days = foldIntoDays(
            listOf(
                SalesHour("2026-09-30T23", "depot", 1, 500.0, 500.0),   // 00:xx on 1 October in Algeria
                SalesHour("2026-09-30T22", "camion", 2, 300.0, 100.0),  // 23:xx on 30 September
                SalesHour("2026-10-01T09", "camion", 1, 200.0, 0.0),
            ),
            range, algiers,
        )

        assertEquals((26..30).map { "2026-09-%02d".format(it) } + listOf("2026-10-01", "2026-10-02"), days.map { it.day.toString() })
        val sept30 = days.single { it.day.toString() == "2026-09-30" }
        assertEquals(SalesFigures(2, 300.0, 100.0), sept30.camion)
        assertEquals(SalesFigures.ZERO, sept30.depot)
        val oct1 = days.single { it.day.toString() == "2026-10-01" }
        assertEquals(SalesFigures(1, 500.0, 500.0), oct1.depot)
        assertEquals(SalesFigures(1, 200.0, 0.0), oct1.camion)
        assertEquals(SalesFigures(2, 700.0, 500.0), oct1.all)
        assertEquals(200.0, oct1.all.credit, 0.0)
        assertEquals(SalesFigures.ZERO, days.last().all)
    }
}
