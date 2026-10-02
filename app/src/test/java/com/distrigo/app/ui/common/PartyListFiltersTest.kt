package com.distrigo.app.ui.common

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Clients and Fournisseurs lists' filter sheets: type, solde and place. */
class PartyListFiltersTest {

    private fun client(
        id: Int, name: String, type: String = "retail", balance: Double = 0.0,
        wilaya: String? = null, commune: String? = null, secteur: String? = null,
    ) = Client(
        id = id, name = name, phone = null, wilaya_id = null, commune_id = null, wilaya_name = wilaya,
        commune_name = commune, secteur_id = null, secteur_name = secteur, address = null, note = null,
        balance = balance, customer_type = type, image_uri = null, latitude = null, longitude = null,
    )

    private fun supplier(id: Int, name: String, balance: Double = 0.0, wilaya: String? = null, commune: String? = null) =
        Supplier(
            id = id, name = name, phone = null, address = null, note = null, balance = balance,
            latitude = null, longitude = null, wilaya_name = wilaya, commune_name = commune,
        )

    private val clients = listOf(
        client(1, "Épicerie Amine", "retail", 1200.0, "Souk Ahras", "Sedrata", "Centre"),
        client(2, "Superette Nadir", "wholesale", 0.0, "souk ahras ", "Souk Ahras", "Nord"),
        client(3, "Alimentation Kamel", "business", -300.0, "Annaba", "El Bouni", null),
        client(4, "Sans adresse", "retail", 0.004, null, null, null),
    )

    private fun names(list: List<Client>) = list.map { it.name }

    @Test
    fun `no criteria keeps the search's result`() {
        assertEquals(clients, filterClients(clients, "", ClientListFilters()))
        assertEquals(listOf("Épicerie Amine"), names(filterClients(clients, "amine", ClientListFilters())))
        assertFalse(ClientListFilters().isActive)
    }

    @Test
    fun `a wilaya matches whatever its case and spaces`() {
        val souk = filterClients(clients, "", ClientListFilters(wilaya = "Souk Ahras"))
        assertEquals(listOf("Épicerie Amine", "Superette Nadir"), names(souk))
        assertEquals(listOf("Épicerie Amine"), names(filterClients(clients, "", ClientListFilters(wilaya = "Souk Ahras", commune = "sedrata"))))
    }

    @Test
    fun `the solde reads half a centime as the solde cell does`() {
        assertEquals(listOf("Épicerie Amine"), names(filterClients(clients, "", ClientListFilters(balance = BalanceFilter.OWING))))
        assertEquals(listOf("Alimentation Kamel"), names(filterClients(clients, "", ClientListFilters(balance = BalanceFilter.ADVANCE))))
        // 0.004 owes nothing a receipt would show.
        assertEquals(listOf("Superette Nadir", "Sans adresse"), names(filterClients(clients, "", ClientListFilters(balance = BalanceFilter.SETTLED))))
    }

    @Test
    fun `criteria combine with the search`() {
        val f = ClientListFilters(type = "retail", secteur = "centre", balance = BalanceFilter.OWING)
        assertTrue(f.isActive)
        assertEquals(listOf("Épicerie Amine"), names(filterClients(clients, "", f)))
        assertEquals(emptyList<String>(), names(filterClients(clients, "nadir", f)))
    }

    @Test
    fun `suppliers filter by place and solde`() {
        val suppliers = listOf(
            supplier(1, "Cevital", 5000.0, "Béjaïa", "Béjaïa"),
            supplier(2, "Hamoud", 0.0, "Alger", "Hussein Dey"),
            supplier(3, "Ifri", -20.0, "Béjaïa", "Ouzellaguen"),
        )
        assertEquals(listOf("Cevital", "Ifri"), filterSuppliers(suppliers, "", SupplierListFilters(wilaya = "béjaïa")).map { it.name })
        assertEquals(listOf("Cevital"), filterSuppliers(suppliers, "", SupplierListFilters(wilaya = "Béjaïa", balance = BalanceFilter.OWING)).map { it.name })
        assertEquals(listOf("Hamoud"), filterSuppliers(suppliers, "ham", SupplierListFilters(balance = BalanceFilter.SETTLED)).map { it.name })
    }

    @Test
    fun `the sheet offers each place once, in order, and none that is blank`() {
        assertEquals(listOf("Annaba", "Souk Ahras"), placesOf(clients.map { it.wilaya_name }))
        assertEquals(listOf("Centre", "Nord"), placesOf(clients.map { it.secteur_name } + "  " + ""))
    }
}
