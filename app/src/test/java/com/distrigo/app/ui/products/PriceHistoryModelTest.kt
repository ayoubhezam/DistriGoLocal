package com.distrigo.app.ui.products

import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.model.withDeltas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The rules behind the price history screen: what each entry's change means, what the filters keep, what the chart plots. */
class PriceHistoryModelTest {

    /** A Sunday, so the week runs Mon 14 → Sun 20 September. */
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

    private val year = PriceHistoryFilters(period = PricePeriod.YEAR)

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
    fun `the period counts back from today, today included`() {
        val week = history.narrow(PriceHistoryFilters(period = PricePeriod.WEEK), today)
        assertEquals(listOf("2026-09-18", "2026-09-16", "2026-09-15"), week.map { it.date.take(10) })
        // 30 days reaches back to 22 August, leaving the purchase of the 20th out.
        val month = history.narrow(PriceHistoryFilters(period = PricePeriod.MONTH), today)
        assertEquals(6, month.size)
        assertEquals(7, history.narrow(year, today).size)
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
        val filters = PriceHistoryFilters(kind = PriceMovementKind.VENTE, period = PricePeriod.WEEK)
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

    // ── The axis ──

    @Test
    fun `a week is seven days, named`() {
        val slots = PricePeriod.WEEK.slotsOn(today)
        assertEquals(7, slots.size)
        assertEquals(LocalDate.of(2026, 9, 14), slots.first().from)
        assertEquals(LocalDate.of(2026, 9, 21), slots.last().until)
        assertEquals(listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"), slots.map { it.label })
    }

    @Test
    fun `thirty days are four weeks, the spare days going to the oldest`() {
        val slots = PricePeriod.MONTH.slotsOn(today)
        assertEquals(listOf("S1", "S2", "S3", "S4"), slots.map { it.label })
        assertEquals(LocalDate.of(2026, 8, 22), slots.first().from)
        assertEquals(LocalDate.of(2026, 9, 21), slots.last().until)
        // Nine days in the first slot, seven in each of the rest: thirty in all.
        assertEquals(listOf(9L, 7L, 7L, 7L), slots.map { java.time.temporal.ChronoUnit.DAYS.between(it.from, it.until) })
    }

    @Test
    fun `twelve months are calendar months, named`() {
        val slots = PricePeriod.YEAR.slotsOn(today)
        assertEquals(12, slots.size)
        assertEquals(LocalDate.of(2025, 10, 1), slots.first().from)
        assertEquals(LocalDate.of(2026, 10, 1), slots.last().until)
        assertEquals("Oct", slots.first().label)
        assertEquals("Sept", slots.last().label)
    }

    // ── The chart ──

    @Test
    fun `a slot holds the quantity-weighted average of what changed hands in it`() {
        // Two cartons at 80 and ten at 90 in the same week: 88.33 per unit, not 85.
        val movements = listOf(
            achat("2026-09-15", 80.0, quantity = 2.0),
            achat("2026-09-15", 90.0, quantity = 10.0),
        ).withDeltas()
        val point = movements.chartSeries(PricePeriod.MONTH, today).single().points.single { !it.carried }
        assertEquals(88.333, point.price, 0.001)
        assertEquals(2, point.count)
    }

    @Test
    fun `a slot without a movement carries the last price forward, and is marked as carried`() {
        val movements = listOf(achat("2026-09-15", 80.0)).withDeltas()
        val points = movements.chartSeries(PricePeriod.WEEK, today).single().points
        // Nothing before the 15th: a price in force cannot precede the first deal.
        assertEquals(listOf("Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"), points.map { it.slot.label })
        assertEquals(listOf(false, true, true, true, true, true), points.map { it.carried })
        assertTrue(points.all { it.price == 80.0 })
        assertEquals(listOf(1, 0, 0, 0, 0, 0), points.map { it.count })
    }

    @Test
    fun `both kinds are plotted on the same slots`() {
        val series = history.chartSeries(PricePeriod.WEEK, today)
        assertEquals(listOf(PriceMovementKind.ACHAT, PriceMovementKind.VENTE), series.map { it.kind })
        // The purchase of the 15th and the sale of the 16th land on their own days.
        assertEquals("Mar", series.first().points.first { !it.carried }.slot.label)
        assertEquals("Mer", series.last().points.first { !it.carried }.slot.label)
    }

    @Test
    fun `a kind with nothing in the period is not plotted`() {
        val achatsOnly = listOf(achat("2026-09-18", 85.0)).withDeltas()
        assertEquals(listOf(PriceMovementKind.ACHAT), achatsOnly.chartSeries(PricePeriod.WEEK, today).map { it.kind })
        assertEquals(emptyList<PriceSeries>(), emptyList<PriceMovement>().chartSeries(PricePeriod.YEAR, today))
    }
}
