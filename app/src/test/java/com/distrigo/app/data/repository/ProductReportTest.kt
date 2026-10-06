package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The Produits report's ranking, groups and ABC classes, from its lines. */
class ProductReportTest {

    private fun line(id: Int, total: Double, cost: Double = total * 0.8, qty: Double = 1.0, category: String? = null, brand: String? = null) =
        ProductSales(id, "P$id", "pièce", null, category, brand, null, qty, total, cost)

    private fun report(vararg lines: ProductSales) = ProductReport(
        ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "", "", null), lines.toList(), emptyList(),
    )

    @Test
    fun `A is the first 80 percent of sales, B the next 15, C the rest`() {
        // 1 000 of sales: 600 + 200 reach 80 %, 100 + 50 the next 15 %, 30 + 20 the last 5 %.
        val r = report(line(1, 600.0), line(2, 200.0), line(3, 100.0), line(4, 50.0), line(5, 30.0), line(6, 20.0))
        assertEquals(listOf("A" to 2, "B" to 2, "C" to 2), r.abc.map { it.label to it.products })
        assertEquals(listOf(800.0, 150.0, 50.0), r.abc.map { it.total })
    }

    @Test
    fun `a product straddling a boundary belongs to the class it starts in`() {
        // 700 starts at 0 %: A. 250 starts at 70 %: A too. 50 starts at 95 %: C.
        val r = report(line(1, 700.0), line(2, 250.0), line(3, 50.0))
        assertEquals(listOf(2, 0, 1), r.abc.map { it.products })
    }

    @Test
    fun `groups sum by value, products without one under Sans, biggest first`() {
        val r = report(line(1, 100.0, category = "Lait"), line(2, 300.0, category = "Huile"), line(3, 50.0, category = " Lait "), line(4, 10.0))
        assertEquals(
            listOf(GroupShare("Huile", 300.0, 1), GroupShare("Lait", 150.0, 2), GroupShare("Sans catégorie", 10.0, 1)),
            r.groups(ProductGrouping.CATEGORIE),
        )
    }

    @Test
    fun `ranking by margin and the rate on cost`() {
        val r = report(line(1, 1000.0, cost = 950.0), line(2, 300.0, cost = 100.0))
        assertEquals(listOf(2, 1), r.ranked(ProductRanking.MARGE).map { it.productId })
        assertEquals(2.0, r.lines[1].marginRate!!, 1e-9)        // 200 ÷ 100
        assertEquals(250.0 / 1050.0, r.marginRate!!, 1e-9)
    }
}
