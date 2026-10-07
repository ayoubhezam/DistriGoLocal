package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The Résultat report's arithmetic: what is left once everything is taken off. */
class ProfitReportTest {

    private fun report(
        sales: Double, cost: Double, returns: Double = 0.0, returnsCost: Double = 0.0,
        charges: Double = 0.0, losses: Double = 0.0,
    ) = ProfitReport(
        ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "", "", null),
        sales, cost, if (returns > 0) 1 else 0, returns, returnsCost,
        if (charges > 0) listOf(ChargeByType("Véhicule", 1, charges)) else emptyList(),
        if (losses > 0) listOf(LossByType("Casse", 1, losses)) else emptyList(),
    )

    @Test
    fun `the net is the gross margin less returns, charges and pertes`() {
        val r = report(sales = 10_000.0, cost = 8_000.0, charges = 500.0, losses = 300.0)
        assertEquals(2_000.0, r.grossMargin, 0.0)
        assertEquals(1_200.0, r.net, 0.0)
        assertEquals(0.12, r.netShare!!, 1e-9)
    }

    @Test
    fun `a sale returned to stock earns nothing`() {
        // Sold 140, cost 100; returned and put back in stock.
        val r = report(sales = 140.0, cost = 100.0, returns = 140.0, returnsCost = 100.0)
        assertEquals(0.0, r.net, 1e-9)
    }

    @Test
    fun `a sale returned and thrown away loses what the goods cost, once`() {
        // Sold 140, cost 100; returned périmé: the margin is cancelled, then the goods are a perte of 100.
        val r = report(sales = 140.0, cost = 100.0, returns = 140.0, returnsCost = 100.0, losses = 100.0)
        assertEquals(-100.0, r.net, 1e-9)
    }

    @Test
    fun `no sales, no share`() {
        assertNull(report(sales = 0.0, cost = 0.0, charges = 50.0).netShare)
    }
}
