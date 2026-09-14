package com.distrigo.app.ui.common

import com.distrigo.app.ui.products.LegacyProductFilters
import com.distrigo.app.ui.products.ProductListFilters
import com.distrigo.app.ui.products.SortOption
import com.distrigo.app.ui.products.filterProducts
import com.distrigo.app.ui.products.sortProducts
import org.junit.Test

/**
 * C6 timing: the inline code against the extracted functions, one call each, at 10 000 rows.
 *
 * This measures what one computation costs. It does not measure the larger effect of C6, which is
 * that the screens now run that computation only when one of its inputs changes rather than on
 * every recomposition; that is structural and is not something a JVM test can time.
 *
 * Printed, not asserted: timings vary by machine, and a test that fails on a slow CI run would say
 * nothing about the code. The table lands in the test report's system-out.
 */
class ListFiltersTimingTest {

    private val products  = ListFilterFixtures.products(seed = 3)
    private val clients   = ListFilterFixtures.clients(seed = 11)
    private val suppliers = ListFilterFixtures.suppliers(seed = 13)
    private val ventes    = ListFilterFixtures.ventes(seed = 17)
    private val orders    = ListFilterFixtures.orders(seed = 19)

    private fun medianMs(block: () -> Any?): Double {
        repeat(8) { block() }
        val samples = List(25) {
            val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1_000_000.0
        }.sorted()
        return samples[samples.size / 2]
    }

    @Test
    fun `timing - inline code against extracted functions at 10K rows`() {
        val q = "bri liq"
        val rows = listOf(
            Triple("Produits: search + sort by name",
                { LegacyProductFilters.productsScreen(products, "bri", ProductListFilters(), SortOption.NAME_ASC) },
                { sortProducts(filterProducts(products, "bri", ProductListFilters()), SortOption.NAME_ASC) }),
            Triple("Produits: sort by name, no search",
                { LegacyProductFilters.productsScreen(products, "", ProductListFilters(), SortOption.NAME_ASC) },
                { sortProducts(filterProducts(products, "", ProductListFilters()), SortOption.NAME_ASC) }),
            Triple("Clients: search",
                { LegacyListFilters.clientsScreen(clients, q, "all", false) },
                { filterClients(clients, q, "all", false) }),
            Triple("Fournisseurs: search",
                { LegacyListFilters.suppliersScreen(suppliers, q) },
                { filterSuppliers(suppliers, q); supplierDebtTotal(suppliers) }),
            Triple("Client picker: search",
                { LegacyListFilters.clientPicker(clients, q) },
                { filterClients(clients, q) }),
            Triple("Vente form: product search",
                { LegacyListFilters.venteFormProducts(products, q) },
                { searchProducts(products, q) }),
            Triple("Inventory: product search",
                { LegacyListFilters.inventorySearch(products, q) },
                { searchProducts(products, q) }),
            Triple("Dépôt Vente: depot, clients, filter, group",
                { LegacyListFilters.ventesScreen(ventes, q, null, null, null, null, null) },
                { val d = depotVentesOf(ventes); clientsOfVentes(d); groupVentesByDay(filterVentes(d, q, VenteListFilters())) }),
            Triple("Achats: suppliers, filter, group",
                { LegacyListFilters.purchasesScreen(orders, q, null, null, null, null, null) },
                { suppliersOfOrders(orders); groupOrdersByDay(filterOrders(orders, q, OrderListFilters())) })
        )

        val report = StringBuilder()
        report.appendLine()
        report.appendLine("C6 timing — one call, ${ListFilterFixtures.ROWS} rows, median of 25 after 8 warm-up runs")
        report.appendLine(String.format("%-44s %12s %12s %9s", "", "inline (ms)", "extracted", "speed-up"))
        for ((label, legacy, extracted) in rows) {
            val before = medianMs(legacy)
            val after  = medianMs(extracted)
            report.appendLine(String.format("%-44s %12.2f %12.2f %8.1fx", label, before, after, before / after))
        }
        println(report)
    }
}
