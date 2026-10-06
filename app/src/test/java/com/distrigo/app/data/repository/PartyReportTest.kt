package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The Clients et fournisseurs report's rankings, average and concentration. */
class PartyReportTest {

    private fun report(vararg parties: PartyFigures) = PartyReport(
        DebtSide.CLIENTS, ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "", "", null), parties.toList(),
    )

    private fun p(id: Int, count: Int, total: Double, cost: Double = 0.0) = PartyFigures(id, "C$id", null, count, total, cost)

    @Test
    fun `ranked by amount, by margin, by number of documents`() {
        val r = report(p(1, 2, 1000.0, 950.0), p(2, 5, 600.0, 300.0), p(3, 5, 800.0, 790.0))
        assertEquals(listOf(1, 3, 2), r.ranked(PartyRanking.MONTANT).map { it.id })
        assertEquals(listOf(2, 1, 3), r.ranked(PartyRanking.MARGE).map { it.id })
        // Equal counts: the bigger total first.
        assertEquals(listOf(3, 2, 1), r.ranked(PartyRanking.DOCUMENTS).map { it.id })
    }

    @Test
    fun `the average document and the share of the biggest`() {
        val r = report(p(1, 2, 600.0), p(2, 3, 300.0), p(3, 5, 100.0))
        assertEquals(100.0, r.average!!, 1e-9)              // 1 000 over 10 documents
        assertEquals(0.9, r.topShare(2)!!, 1e-9)            // 600 + 300 of 1 000
        assertNull(report().average)
        assertNull(report().topShare(5))
    }
}
