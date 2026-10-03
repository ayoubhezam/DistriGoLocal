package com.distrigo.app.ui.products

import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.model.withDeltas
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The rules behind the price history screen: what each entry's change means and what the filters keep. */
class PriceHistoryModelTest {

    /** A Sunday: « 7 derniers jours » runs from Mon 14 to Sun 20 September. */
    private val today = LocalDate.of(2026, 9, 20)

    private var nextId = 1
    private fun movement(
        kind: PriceMovementKind, day: String, price: Double, party: String = "Sarl El Manar", quantity: Double = 10.0,
    ) = PriceMovement(
        kind = kind, documentId = nextId++, documentLabel = "#${nextId}", party = party,
        date = day + "T10:00:00Z", unitPrice = price, quantity = quantity,
    )

    private fun achat(day: String, price: Double, party: String = "Sarl El Manar", quantity: Double = 10.0) =
        movement(PriceMovementKind.ACHAT, day, price, party, quantity)

    private fun vente(day: String, price: Double, party: String = "Client N°245", quantity: Double = 10.0) =
        movement(PriceMovementKind.VENTE, day, price, party, quantity)

    /** Newest first, as the query returns them. */
    private val history = listOf(
        achat("2026-09-18", 85.0),
        vente("2026-09-16", 95.0),
        achat("2026-09-15", 80.0),
        vente("2026-09-12", 95.0),
        achat("2026-09-02", 82.0, party = "Ets Benali"),
        vente("2026-09-05", 92.0),
        achat("2026-08-20", 78.0),
    ).sortedByDescending { it.date }.withDeltas()

    private val year = PriceHistoryFilters(period = ReportFilter(ReportPeriod.CETTE_ANNEE))

    // ── Deltas ──

    @Test
    fun `a movement is measured against the one before it of its own kind`() {
        val deltas = history.associate { it.kind.label + " " + it.date.take(10) to it.delta }
        // 85 follows the 80 bought on the 15th — not the 95 sold on the 16th.
        assertEquals(5.0, deltas.getValue("Achat 2026-09-18")!!, 0.0)
        assertEquals(-2.0, deltas.getValue("Achat 2026-09-15")!!, 0.0)
        assertEquals(4.0, deltas.getValue("Achat 2026-09-02")!!, 0.0)
        assertEquals(0.0, deltas.getValue("Vente 2026-09-16")!!, 0.0)
        assertEquals(3.0, deltas.getValue("Vente 2026-09-12")!!, 0.0)
    }

    @Test
    fun `the first movement of each kind has no change to show`() {
        assertNull(history.first { it.kind == PriceMovementKind.ACHAT && it.date.startsWith("2026-08-20") }.delta)
        assertNull(history.first { it.kind == PriceMovementKind.VENTE && it.date.startsWith("2026-09-05") }.delta)
    }

    // ── Filters ──

    @Test
    fun `the kind segments the list`() {
        val achats = history.narrow(year.copy(kind = PriceMovementKind.ACHAT), today)
        assertEquals(4, achats.size)
        assertEquals(listOf(PriceMovementKind.ACHAT), achats.map { it.kind }.distinct())
    }

    @Test
    fun `the period is the Rapports period, today included`() {
        fun on(period: ReportFilter) = history.narrow(PriceHistoryFilters(period = period), today)
        assertEquals(listOf("2026-09-18", "2026-09-16", "2026-09-15"), on(ReportFilter(ReportPeriod.SEPT_JOURS)).map { it.date.take(10) })
        // This month starts on the 1st, leaving the purchase of 20 August out.
        assertEquals(6, on(ReportFilter(ReportPeriod.CE_MOIS)).size)
        assertEquals(listOf("2026-08-20"), on(ReportFilter(ReportPeriod.MOIS_DERNIER)).map { it.date.take(10) })
        assertEquals(7, history.narrow(year, today).size)
    }

    @Test
    fun `a custom period keeps both of its days`() {
        val custom = ReportFilter(ReportPeriod.PERSONNALISE, customFrom = LocalDate.of(2026, 8, 20), customTo = LocalDate.of(2026, 9, 2))
        assertEquals(listOf("2026-09-02", "2026-08-20"), history.narrow(PriceHistoryFilters(period = custom), today).map { it.date.take(10) })
    }

    @Test
    fun `variation keeps only rises or only falls`() {
        val up = history.narrow(year.copy(variation = PriceVariation.UP), today)
        assertEquals(listOf(5.0, 3.0, 4.0), up.map { it.delta })
        val down = history.narrow(year.copy(variation = PriceVariation.DOWN), today)
        assertEquals(listOf(-2.0), down.map { it.delta })
    }

    @Test
    fun `the search reads the party, the kind and the document`() {
        assertEquals(1, history.narrow(year.copy(query = "benali"), today).size)
        assertEquals(3, history.narrow(year.copy(query = "vente"), today).size)
        // Every word must appear, as elsewhere in the app.
        assertEquals(0, history.narrow(year.copy(query = "benali vente"), today).size)
    }

    @Test
    fun `sorting by price keeps the newest first within a price`() {
        val cheapest = history.narrow(year.copy(sort = PriceSort.CHEAPEST), today)
        assertEquals(listOf(78.0, 80.0, 82.0, 85.0, 92.0, 95.0, 95.0), cheapest.map { it.unitPrice })
        // The two sales at 95 keep the newest of them first, so the oldest is last of all.
        assertEquals("2026-09-12", cheapest.last().date.take(10))
        val oldest = history.narrow(year.copy(sort = PriceSort.OLDEST), today)
        assertEquals("2026-08-20", oldest.first().date.take(10))
    }

    @Test
    fun `counting a kind ignores the segment but not the other filters`() {
        val filters = PriceHistoryFilters(kind = PriceMovementKind.VENTE, period = ReportFilter(ReportPeriod.SEPT_JOURS))
        val counted = history.narrow(filters, today, includeKind = false)
        assertEquals(3, counted.size)
        assertEquals(2, counted.count { it.kind == PriceMovementKind.ACHAT })
    }

    // ── Summaries ──

    @Test
    fun `a kind's summary is its last, average, lowest and highest price`() {
        val achats = history.statsOf(PriceMovementKind.ACHAT)!!
        assertEquals(85.0, achats.last, 0.0)
        assertEquals(81.25, achats.average, 0.001)
        assertEquals(78.0, achats.min, 0.0)
        assertEquals(85.0, achats.max, 0.0)
        assertNull(emptyList<PriceMovement>().statsOf(PriceMovementKind.VENTE))
    }
}
