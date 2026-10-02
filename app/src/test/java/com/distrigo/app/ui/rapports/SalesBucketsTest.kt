package com.distrigo.app.ui.rapports

import com.distrigo.app.data.repository.SalesDay
import com.distrigo.app.data.repository.SalesFigures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** A bar a day up to 62 days, a bar a month beyond, every sale in exactly one bar. */
class SalesBucketsTest {

    private fun days(from: String, count: Long) = (0 until count).map {
        val day = LocalDate.parse(from).plusDays(it)
        SalesDay(day, SalesFigures(1, 100.0, 60.0), if (day.dayOfMonth == 1) SalesFigures(1, 50.0, 0.0) else SalesFigures.ZERO)
    }

    @Test
    fun `up to 62 days there is a bar a day`() {
        val buckets = salesBuckets(days("2026-08-01", 62))
        assertEquals(62, buckets.size)
        assertEquals("01/08", buckets.first().label)
        assertEquals("Sam. 01/08/2026", buckets.first().title)
        assertEquals(SalesFigures(2, 150.0, 60.0), buckets.first().all)
    }

    @Test
    fun `beyond 62 days the days are summed into months`() {
        val buckets = salesBuckets(days("2026-01-01", 275))   // 1 January to 2 October
        assertEquals((1..10).map { LocalDate.of(2026, it, 1) }, buckets.map { it.start })
        assertEquals("Janvier 2026", buckets.first().title)
        assertEquals(SalesFigures(31, 3100.0, 1860.0), buckets.first().depot)
        assertEquals(SalesFigures(1, 50.0, 0.0), buckets.first().camion)
        assertEquals(SalesFigures(2, 200.0, 120.0), buckets.last().depot)
        assertEquals(275, buckets.sumOf { it.depot.count })
    }
}
