package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The taux de marge is on the purchase price: (ventes − coût) ÷ coût. */
class MarginRateTest {

    private fun report(total: Double, cost: Double) = SalesReport(
        range = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), "", "", null),
        depot = SalesFigures(1, total, total), camion = SalesFigures.ZERO, clientsServed = 1,
        cost = cost, estimatedCost = 0.0, returns = null, days = emptyList(),
    )

    @Test
    fun `the rate is the margin over the cost`() {
        // Bought 1 040, sold 1 248: 208 of margin, 20 % of the cost (16.7 % of the sale, as it was).
        assertEquals(0.20, report(total = 1248.0, cost = 1040.0).marginRate!!, 1e-9)
    }

    @Test
    fun `a sale below cost gives a negative rate`() {
        assertEquals(-0.25, report(total = 75.0, cost = 100.0).marginRate!!, 1e-9)
    }

    @Test
    fun `no cost, no rate`() {
        assertNull(report(total = 500.0, cost = 0.0).marginRate)
    }
}
