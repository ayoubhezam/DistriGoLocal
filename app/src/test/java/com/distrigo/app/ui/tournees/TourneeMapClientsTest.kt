package com.distrigo.app.ui.tournees

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.TourneeClientInfo
import com.distrigo.app.data.repository.VisitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Carte de la tournée »: how each client's visit is read, and which can be pinned. */
class TourneeMapClientsTest {

    private fun client(id: Int, lat: Double? = 36.28, lng: Double? = 7.95, quartier: String? = null, secteur: String? = "Centre", address: String? = null) =
        Client(
            id = id, name = "C$id", phone = null, wilaya_id = null, commune_id = null, wilaya_name = "Souk Ahras",
            commune_name = "Souk Ahras", secteur_id = 1, secteur_name = secteur, address = address, note = null,
            balance = 0.0, customer_type = "retail", image_uri = null, latitude = lat, longitude = lng, quartier_name = quartier
        )

    private fun info(c: Client, status: String) = TourneeClientInfo(c, status, null)

    @Test
    fun aVisitReadsAsTheDashboardReadsIt() {
        val clients = tourneeMapClients(
            onList = listOf(
                info(client(1), "visite"),        // visited, bought
                info(client(2), "visite"),        // visited, no sale
                info(client(3), "a_visiter"),     // bought without being marked
                info(client(4), "a_visiter"),     // still to visit
                info(client(5), "en_cours"),      // being visited
            ),
            offListBuyers = listOf(client(6), client(1)),
            buyers = setOf(1, 3, 6),
        )
        assertEquals(
            listOf(1 to VisitState.WITH_SALE, 2 to VisitState.WITHOUT_SALE, 3 to VisitState.WITH_SALE,
                4 to VisitState.PENDING, 5 to VisitState.PENDING, 6 to VisitState.WITH_SALE),
            clients.map { it.id to it.state }
        )
    }

    @Test
    fun onlyARealPositionIsPinned() {
        val clients = tourneeMapClients(
            listOf(client(1), client(2, lat = null), client(3, lat = 0.0, lng = 0.0), client(4, lat = 120.0)).map { info(it, "a_visiter") },
            emptyList(), emptySet()
        )
        assertEquals(listOf(true, false, false, false), clients.map { it.located })
    }

    @Test
    fun thePlaceIsTheQuartierSecteurAndCommuneElseTheAddress() {
        val clients = tourneeMapClients(
            listOf(
                info(client(1, quartier = "Gare"), "a_visiter"),
                info(client(2, secteur = null).copy(commune_name = null, address = "Rue 5"), "a_visiter"),
            ),
            emptyList(), emptySet()
        )
        assertEquals("Gare · Centre · Souk Ahras", clients[0].place)
        assertEquals("Rue 5", clients[1].place)
        assertTrue(clients.all { it.located })
        assertFalse(clients[1].place.isNullOrBlank())
    }
}
