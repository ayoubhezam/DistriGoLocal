package com.distrigo.app.ui.products

import com.distrigo.app.ui.common.ListFilterFixtures
import com.distrigo.app.ui.common.ListFilterFixtures.QUERIES
import com.distrigo.app.ui.common.ListFilterFixtures.show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * C6: the Produits screen's extracted filter and sort return exactly what its inline code returned —
 * the same products, in the same order, ties included — on a randomized 10 000-product catalogue.
 */
class ProductListFiltersEquivalenceTest {

    private val products = ListFilterFixtures.products(seed = 7)

    private fun assertSame(query: String, filters: ProductListFilters, sort: SortOption): Pair<Int, Int> {
        val (legacyFiltered, legacySorted) = LegacyProductFilters.productsScreen(products, query, filters, sort)
        val filtered = filterProducts(products, query, filters)
        val sorted   = sortProducts(filtered, sort)
        assertEquals("filter, query=${show(query)} $filters", legacyFiltered, filtered)
        assertEquals("sort, query=${show(query)} $filters $sort", legacySorted, sorted)
        return legacyFiltered.size to products.size
    }

    @Test
    fun `every search under every sort matches the inline code`() {
        var partial = 0
        for (q in QUERIES) for (sort in SortOption.values()) {
            val (kept, all) = assertSame(q, ProductListFilters(), sort)
            if (kept in 1 until all) partial++
        }
        assertTrue("only $partial partial cases", partial > 150)
    }

    @Test
    fun `random filter combinations match the inline code`() {
        val r = Random(2026)
        var partial = 0
        repeat(260) {
            val (kept, all) = assertSame(QUERIES.random(r), ListFilterFixtures.productFilters(r), SortOption.values().random(r))
            if (kept in 1 until all) partial++
        }
        assertTrue("only $partial partial cases", partial > 60)
    }

    @Test
    fun `every filter on its own, over the whole catalogue`() {
        val singles = listOf(
            ProductListFilters(categoryId = 2), ProductListFilters(sousCategorieId = 1),
            ProductListFilters(marqueId = 2), ProductListFilters(supplierId = 3),
            ProductListFilters(unitType = "kg"), ProductListFilters(unitType = ""),
            ProductListFilters(stockLevel = "in_stock"), ProductListFilters(stockLevel = "low_stock"),
            ProductListFilters(stockLevel = "out_of_stock"), ProductListFilters(stockLevel = "weird"),
            ProductListFilters(priceMin = "100"), ProductListFilters(priceMax = "140"),
            ProductListFilters(priceMin = "abc", priceMax = "xyz"), ProductListFilters(expiringSoon = true)
        )
        for (f in singles) for (sort in SortOption.values()) assertSame("", f, sort)
    }

    @Test
    fun `the randomized catalogue really contains the cases worth testing`() {
        // Names that differ but share a lowercase form, so the name sorts meet ties.
        assertTrue(products.groupBy { it.name.lowercase() }.values.any { g -> g.map { it.name }.distinct().size > 1 })
        // Products the expiring-soon filter keeps.
        assertTrue(products.any { it.has_expiry == 1 && isExpiringSoon(it.expiry_date) })
        // Stock in every band.
        assertTrue(products.any { it.stock > it.min_stock } && products.any { it.stock <= 0 } && products.any { it.stock in 1.0..it.min_stock.toDouble() })
    }

    @Test
    fun `ties in the lowercase name keep their original order, as before`() {
        val names = listOf("BRILEX", "brilex", "Café", "Brilex", "İ", "CAFÉ", "i", "café", "zeta", "Zeta")
        val crafted = names.mapIndexed { i, n -> products[i].copy(id = 900_000 + i, name = n) }
        for (sort in listOf(SortOption.NAME_ASC, SortOption.NAME_DESC)) {
            val legacy = LegacyProductFilters.productsScreen(crafted, "", ProductListFilters(), sort).second
            assertEquals(sort.name, legacy.map { it.id }, sortProducts(crafted, sort).map { it.id })
        }
    }

    @Test
    fun `a case-insensitive comparator would not have kept the order - which is why it is not used`() {
        val crafted = listOf("İ", "i").mapIndexed { i, n -> products[i].copy(id = 910_000 + i, name = n) }
        val kept = sortProducts(crafted, SortOption.NAME_ASC).map { it.name }
        val caseInsensitive = crafted.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).map { it.name }
        assertNotEquals(kept, caseInsensitive)
    }
}
