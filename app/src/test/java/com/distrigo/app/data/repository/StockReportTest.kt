package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The Stock et pertes report's counts and rate, from its parts. */
class StockReportTest {

    private fun report(restock: List<RestockLine> = emptyList(), losses: List<LossByType> = emptyList(), salesCost: Double = 0.0) = StockReport(
        ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "", "", null),
        StockValue(1000.0, 250.0, 3), restock, losses, emptyList(), salesCost,
    )

    private fun line(stock: Double, min: Double = 10.0) = RestockLine(1, "P", "pièce", null, stock, min)

    @Test
    fun `out of stock is nothing or less, low is some but under the minimum`() {
        val r = report(restock = listOf(line(0.0), line(-3.0), line(0.0004), line(2.0), line(9.5)))
        assertEquals(3, r.outOfStock)
        assertEquals(2, r.lowStock)
    }

    @Test
    fun `the loss rate is the pertes over what the sales cost`() {
        val r = report(losses = listOf(LossByType("Casse", 2, 300.0), LossByType("Vol", 1, 200.0)), salesCost = 10_000.0)
        assertEquals(500.0, r.lossValue, 0.0)
        assertEquals(3, r.lossCount)
        assertEquals(0.05, r.lossRate!!, 1e-9)
        assertNull(report(losses = listOf(LossByType("Casse", 1, 50.0))).lossRate)
    }

    @Test
    fun `the stock's value is the depot's and the camion's`() {
        assertEquals(1250.0, report().stock.total, 0.0)
    }
}
