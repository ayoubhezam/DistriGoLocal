package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.ClientPlaceSalesRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Répartition des ventes »: the tree [salesPlaces] builds, and the ring [ringSlices] draws of a place. */
class SalesPlacesTest {

    private fun row(
        id: Int, total: Double, wilaya: String? = "Souk Ahras", commune: String? = "Sedrata",
        secteurId: Int? = 1, secteur: String? = "Centre", quartierId: Int? = 5, quartier: String? = "Skanska",
        name: String? = "C$id", count: Int = 1,
    ) = ClientPlaceSalesRow(id, name, wilaya, commune, secteurId, secteur, quartierId, quartier, count, total)

    private fun PlaceNode.sumsUp() {
        if (children.isEmpty()) return
        assertEquals("$name adds up", total, children.sumOf { it.total }, 0.001)
        assertEquals("$name counts up", sales, children.sumOf { it.sales })
        children.forEach { it.sumsUp() }
    }

    @Test
    fun everyLevelAddsUpToTheOneAboveAndTheRootToTheSales() {
        val tree = salesPlaces(
            listOf(
                row(1, 1000.0, count = 2), row(2, 250.5), row(3, 4000.0, commune = "M'daourouche", secteurId = 2, secteur = "Gare", quartierId = 7, quartier = "Cité"),
                row(4, 99.99, wilaya = "Guelma", commune = "Guelma", secteurId = 3, secteur = "Ville", quartierId = null, quartier = null),
            )
        )
        assertEquals(5350.49, tree.total, 0.001)
        assertEquals(5, tree.sales)
        tree.sumsUp()
        assertEquals(listOf("Souk Ahras", "Guelma"), tree.children.map { it.name })
        // Every client once, at the bottom
        assertEquals(4, tree.children.flatMap { w -> w.children.flatMap { c -> c.children.flatMap { s -> s.children.flatMap { q -> q.children } } } }.size)
    }

    @Test
    fun aClientWithoutAPlaceCountsUnderSansAtThatLevel() {
        val tree = salesPlaces(listOf(row(1, 100.0, wilaya = null, commune = " ", secteurId = null, secteur = null, quartierId = null, quartier = null)))
        val wilaya = tree.children.single()
        assertEquals(NO_WILAYA, wilaya.name)
        val commune = wilaya.children.single()
        assertEquals(NO_COMMUNE_NAME, commune.name)
        val secteur = commune.children.single()
        assertEquals(NO_SECTEUR, secteur.name)
        val quartier = secteur.children.single()
        assertEquals(NO_QUARTIER, quartier.name)
        assertEquals(1, quartier.children.single().clientId)
    }

    @Test
    fun aCommuneIsItsWilayasAndTwoSecteursOfOneNameStayTwo() {
        val tree = salesPlaces(
            listOf(
                row(1, 10.0, wilaya = "A", commune = "Centre", secteurId = 1, secteur = "Nord"),
                row(2, 20.0, wilaya = "B", commune = "centre", secteurId = 2, secteur = "Nord"),
                row(3, 30.0, wilaya = "A", commune = "CENTRE", secteurId = 3, secteur = "Nord", quartierId = 8),
            )
        )
        val a = tree.children.first { it.name == "A" }
        assertEquals(1, a.children.size) // « Centre » and « CENTRE » in A are one commune
        assertEquals(2, a.children.single().children.size) // secteurs 1 and 3, both « Nord »
        assertEquals(1, tree.children.first { it.name == "B" }.children.size)
        tree.sumsUp()
    }

    @Test
    fun theLargestComeFirstAndKeysFindThem() {
        val tree = salesPlaces(listOf(row(1, 10.0, commune = "Petite"), row(2, 500.0, commune = "Grande"), row(3, 10.0, commune = "Autre")))
        val communes = tree.children.single().children
        assertEquals(listOf("Grande", "Autre", "Petite"), communes.map { it.name }) // equal totals: by name
        communes.forEach { assertEquals(it, tree.find(it.key)) }
        assertNull(tree.find("nowhere"))
        assertTrue(communes.all { it.key.startsWith(tree.children.single().key + "|") })
    }

    @Test
    fun noSaleIsAnEmptyTree() {
        val tree = salesPlaces(emptyList())
        assertEquals(0.0, tree.total, 0.0)
        assertTrue(tree.children.isEmpty())
        assertTrue(ringSlices(tree).isEmpty())
    }

    @Test
    fun aRingShowsFiveAndGathersTheRestInAutres() {
        val rows = (1..8).map { row(it, it * 100.0, commune = "Commune $it") }
        val wilaya = salesPlaces(rows).children.single()
        val slices = ringSlices(wilaya)
        assertEquals(6, slices.size)
        assertEquals(listOf("Commune 8", "Commune 7", "Commune 6", "Commune 5", "Commune 4", "Autres"), slices.map { it.name })
        val others = slices.last()
        assertTrue(others.isOthers)
        assertEquals(listOf("Commune 3", "Commune 2", "Commune 1"), others.others.map { it.name })
        assertEquals(600.0, others.total, 0.001)
        assertEquals(1.0, slices.sumOf { it.share }, 1e-9)
    }

    @Test
    fun fiveOrFewerAreAllShown() {
        val wilaya = salesPlaces((1..5).map { row(it, 10.0, commune = "C$it") }).children.single()
        val slices = ringSlices(wilaya)
        assertEquals(5, slices.size)
        assertTrue(slices.none { it.isOthers })
    }
}
