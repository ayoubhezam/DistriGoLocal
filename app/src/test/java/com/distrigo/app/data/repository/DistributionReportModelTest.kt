package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the distribution shows of what the query returned: the bars drawn, the communes offered. */
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
    fun `the selector offers the communes by name, then the clients without one`() {
        val r = report(emptyList()).copy(communes = listOf(
            CommuneStat("Souk Ahras", 9, 6, 3, 4, 2.0 / 3),
            CommuneStat("ain zana", 4, 1, 3, 1, 0.25),
            CommuneStat("Sedrata", 5, 5, 0, 2, 1.0),
            CommuneStat(null, 3, 1, 2, 0, 1.0 / 3),
        ))
        assertEquals(listOf("ain zana", "Sedrata", "Souk Ahras", NO_COMMUNE), r.communeChoices)
        // No client without a commune, no such choice.
        val all = r.copy(communes = r.communes.filter { it.name != null })
        assertEquals(listOf("ain zana", "Sedrata", "Souk Ahras"), all.communeChoices)
    }
}
