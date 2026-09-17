package com.distrigo.app.ui.common

import com.distrigo.app.ui.common.ListFilterFixtures.CUSTOMER_TYPES
import com.distrigo.app.ui.common.ListFilterFixtures.QUERIES
import com.distrigo.app.ui.common.ListFilterFixtures.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * C6: every extracted list function returns exactly what the inline code it replaced returned —
 * the same elements, in the same order — on randomized 10 000-row lists.
 *
 * "Partial" counts combinations that kept some rows and dropped others. Each test requires plenty
 * of them, so a fixture that happened to match everything or nothing cannot pass vacuously.
 */
class ListFiltersEquivalenceTest {

    private val products  = ListFilterFixtures.products(seed = 3)
    private val clients   = ListFilterFixtures.clients(seed = 11)
    private val suppliers = ListFilterFixtures.suppliers(seed = 13)
    private val ventes    = ListFilterFixtures.ventes(seed = 17)
    private val orders    = ListFilterFixtures.orders(seed = 19)

    private fun <T> partial(result: List<T>, all: List<T>) = result.isNotEmpty() && result.size < all.size

    @Test
    fun `search tokens are the same words the inline split produced`() {
        for (q in QUERIES) {
            assertEquals("query=${show(q)}", q.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }, searchTokens(q))
        }
    }

    @Test
    fun `Clients screen - search, customer type and debt filter`() {
        var partial = 0
        val cases = QUERIES.map { Triple(it, "all", false) } +
            listOf("", "a", "bri liq", "0555", "é", "İ", "   ", "zzz").flatMap { q ->
                CUSTOMER_TYPES.flatMap { t -> listOf(Triple(q, t, false), Triple(q, t, true)) }
            }
        for ((q, type, debt) in cases) {
            val legacy = LegacyListFilters.clientsScreen(clients, q, type, debt)
            assertEquals("query=${show(q)} type=$type debt=$debt", legacy, filterClients(clients, q, type, debt))
            if (partial(legacy, clients)) partial++
        }
        assertTrue("only $partial partial cases", partial > 60)
    }

    @Test
    fun `Clients screen - debt count and total`() {
        val (legacyClients, legacyTotal) = LegacyListFilters.clientsDebt(clients)
        val debtClients = clientsInDebt(clients)
        assertEquals(legacyClients, debtClients)
        assertEquals(legacyTotal, debtClients.sumOf { it.balance }, 0.0)
        assertTrue(partial(legacyClients, clients))
    }

    @Test
    fun `Fournisseurs screen - search and debt total`() {
        var partial = 0
        for (q in QUERIES) {
            val (legacy, legacyTotal) = LegacyListFilters.suppliersScreen(suppliers, q)
            assertEquals("query=${show(q)}", legacy, filterSuppliers(suppliers, q))
            assertEquals(legacyTotal, supplierDebtTotal(suppliers), 0.0)
            if (partial(legacy, suppliers)) partial++
        }
        assertTrue("only $partial partial cases", partial > 30)
    }

    @Test
    fun `client picker - search with the defaults`() {
        var partial = 0
        for (q in QUERIES) {
            val legacy = LegacyListFilters.clientPicker(clients, q)
            assertEquals("query=${show(q)}", legacy, filterClients(clients, q))
            if (partial(legacy, clients)) partial++
        }
        assertTrue("only $partial partial cases", partial > 30)
    }

    @Test
    fun `Vente form product step and inventory dialog - product search`() {
        var partial = 0
        for (q in QUERIES) {
            val legacyForm = LegacyListFilters.venteFormProducts(products, q)
            val legacyInventory = LegacyListFilters.inventorySearch(products, q)
            val now = searchProducts(products, q)
            assertEquals("vente form, query=${show(q)}", legacyForm, now)
            assertEquals("inventory, query=${show(q)}", legacyInventory, now)
            if (partial(legacyForm, products)) partial++
        }
        assertTrue("only $partial partial cases", partial > 30)
    }

    @Test
    fun `Depot Vente screen - depot list, client choices, filters and day grouping`() {
        val r = Random(1717)
        var partial = 0
        val cases = QUERIES.map { it to VenteListFilters() } + List(220) { QUERIES.random(r) to ListFilterFixtures.venteFilters(r) }
        for ((q, f) in cases) {
            val legacy = LegacyListFilters.ventesScreen(ventes, q, f.status, f.paymentStatus, f.clientId, f.dateFrom, f.dateTo)

            val depot    = depotVentesOf(ventes)
            // In UTC, where a stored instant's day is its first ten characters, as the inline code read it.
            // The local-day behaviour is covered in ListFiltersLocalDayTest.
            val filtered = filterVentes(depot, q, f, ZoneOffset.UTC)
            val grouped  = groupVentesByDay(filtered, ZoneOffset.UTC)

            val msg = "query=${show(q)} $f"
            assertEquals(msg, legacy.depotVentes, depot)
            assertEquals(msg, legacy.clients, clientsOfVentes(depot))
            assertEquals(msg, legacy.filteredVentes, filtered)
            // Map equality ignores order; the list's day headers do not.
            assertEquals(msg, legacy.groupedVentes.entries.toList(), grouped.entries.toList())
            if (partial(legacy.filteredVentes, legacy.depotVentes)) partial++
        }
        assertTrue("only $partial partial cases", partial > 100)
    }

    @Test
    fun `Achats screen - supplier choices, filters and day grouping`() {
        val r = Random(1919)
        var partial = 0
        val cases = QUERIES.map { it to OrderListFilters() } + List(220) { QUERIES.random(r) to ListFilterFixtures.orderFilters(r) }
        for ((q, f) in cases) {
            val legacy = LegacyListFilters.purchasesScreen(orders, q, f.receptionStatus, f.paymentStatus, f.supplierId, f.dateFrom, f.dateTo)

            val filtered = filterOrders(orders, q, f, ZoneOffset.UTC)
            val grouped  = groupOrdersByDay(filtered, ZoneOffset.UTC)

            val msg = "query=${show(q)} $f"
            assertEquals(msg, legacy.suppliers, suppliersOfOrders(orders))
            assertEquals(msg, legacy.filteredOrders, filtered)
            assertEquals(msg, legacy.groupedOrders.entries.toList(), grouped.entries.toList())
            if (partial(legacy.filteredOrders, orders)) partial++
        }
        assertTrue("only $partial partial cases", partial > 100)
    }
}
