package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the distribution shows of what the query returned: the bars drawn, the commune filters. */
class DistributionReportModelTest {

    private fun sector(id: Int, clients: Int) = SectorStat(id, "S$id", "Souk Ahras", clients, 0, clients, 0.0)

    private fun report(sectors: List<SectorStat>) = DistributionReport(
        clients = sectors.sumOf { it.clients }, served = 0, rate = 0.0, unsectored = 0, sectors = sectors, communes = emptyList(),
    )

    @Test
    fun `seven bars at most, the largest first as the query sorted them`() {
        val r = report((1..10).map { sector(it, 20 - it) })
        assertEquals((1..7).toList(), r.topSectors.map { it.id })
    }

    @Test
    fun `fewer sectors, fewer bars, and none for a sector without a client`() {
        val r = report(listOf(sector(1, 5), sector(2, 3), sector(3, 0)))
        assertEquals(listOf(1, 2), r.topSectors.map { it.id })
    }

    @Test
    fun `the clients without a commune are filtered as an empty commune`() {
        val none = CommuneStat(null, 3, 1, 2, 0, 1.0 / 3)
        assertEquals(CommuneFilter(""), none.filter)
        assertEquals("Sans commune", none.filter.label)
        assertEquals("Sedrata", CommuneStat("Sedrata", 1, 1, 0, 1, 1.0).filter.label)
    }
}
