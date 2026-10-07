package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The Tournées report's totals and rates, all tournées of the period together. */
class TourReportTest {

    private val range = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "", "", null)

    private fun tour(id: Int, planned: Int, visited: Int, sales: Int, total: Double, paid: Double, open: Boolean = false) =
        TourFigures(id, "T$id", LocalDate.of(2026, 9, id), open, planned, visited, sales, sales, total, paid)

    @Test
    fun `totals and rates add the tournées up`() {
        val r = TourReport(range, listOf(
            tour(2, planned = 10, visited = 8, sales = 6, total = 60_000.0, paid = 45_000.0, open = true),
            tour(1, planned = 10, visited = 4, sales = 3, total = 20_000.0, paid = 20_000.0),
        ))
        assertEquals(80_000.0, r.total, 0.0)
        assertEquals(65_000.0, r.paid, 0.0)
        assertEquals(9, r.sales)
        assertEquals(1, r.openCount)
        assertEquals(0.6, r.visitRate!!, 1e-9)
        assertEquals(40_000.0, r.perTour!!, 0.0)
    }

    @Test
    fun `no tournée, no average and no rate`() {
        val r = TourReport(range, emptyList())
        assertNull(r.perTour)
        assertNull(r.visitRate)
        assertNull(r.paidRate)
    }
}
