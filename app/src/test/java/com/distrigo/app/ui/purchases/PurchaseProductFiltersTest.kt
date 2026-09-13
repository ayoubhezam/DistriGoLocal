package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchaseProductFiltersTest {

    private val today = LocalDate.of(2026, 9, 13)

    private fun product(
        name          : String  = "Thé rouge 500g",
        barcode       : String? = "6130000000017",
        purchasePrice : Double  = 140.0,
        sellingPrice  : Double  = 250.0,
        stock         : Double  = 10.0,
        minStock      : Int     = 5,
        unitType      : String  = "pièce",
        hasExpiry     : Int     = 0,
        expiryDate    : String? = null,
        categoryId    : Int?    = 1,
        sousCatId     : Int?    = 11,
        marqueId      : Int?    = 21,
        supplierId    : Int?    = 31
    ) = Product(
        id = 1, name = name, barcode = barcode,
        selling_price = sellingPrice, purchase_price = purchasePrice,
        stock = stock, min_stock = minStock, unit_type = unitType,
        packages = 0, pack_size = 0,
        has_expiry = hasExpiry, expiry_date = expiryDate, image_uri = null,
        category_name = null, category_id = categoryId,
        supplier_name = null, supplier_id = supplierId,
        sous_categorie_id = sousCatId, marque_id = marqueId
    )

    // ── the empty filter ─────────────────────────────────────────────────────

    @Test
    fun `no filter is inactive and keeps every product`() {
        val none = ProductListFilters()
        assertEquals(0, none.activeCount)
        assertFalse(none.isActive)
        assertTrue(product().matches(none, today))
    }

    // ── search: unchanged from what step 02 did before ───────────────────────

    @Test
    fun `search needs every word in the name or the barcode`() {
        val p = product()
        assertTrue(p.matchesSearch(""))
        assertTrue(p.matchesSearch("   "))
        assertTrue(p.matchesSearch("rouge THÉ"))
        assertTrue(p.matchesSearch("6130000"))
        assertFalse(p.matchesSearch("rouge vert"))
    }

    // ── one criterion at a time ──────────────────────────────────────────────

    @Test
    fun `ids narrow by category, sous-categorie, marque and supplier`() {
        val p = product()
        assertTrue(p.matches(ProductListFilters(categoryId = 1), today))
        assertFalse(p.matches(ProductListFilters(categoryId = 2), today))
        assertTrue(p.matches(ProductListFilters(sousCategorieId = 11), today))
        assertFalse(p.matches(ProductListFilters(sousCategorieId = 12), today))
        assertTrue(p.matches(ProductListFilters(marqueId = 21), today))
        assertFalse(p.matches(ProductListFilters(marqueId = 22), today))
        assertTrue(p.matches(ProductListFilters(supplierId = 31), today))
        assertFalse(p.matches(ProductListFilters(supplierId = 32), today))
    }

    @Test
    fun `unit type narrows to carton or piece`() {
        assertTrue(product(unitType = "pièce").matches(ProductListFilters(unitType = "pièce"), today))
        assertFalse(product(unitType = "carton").matches(ProductListFilters(unitType = "pièce"), today))
    }

    @Test
    fun `stock bands match the Produits sheet`() {
        val inStock  = ProductListFilters(stockLevel = "in_stock")
        val low      = ProductListFilters(stockLevel = "low_stock")
        val out      = ProductListFilters(stockLevel = "out_of_stock")

        assertTrue(product(stock = 10.0, minStock = 5).matches(inStock, today))
        assertFalse(product(stock = 5.0, minStock = 5).matches(inStock, today))

        assertTrue(product(stock = 5.0, minStock = 5).matches(low, today))
        assertTrue(product(stock = 1.0, minStock = 5).matches(low, today))
        assertFalse(product(stock = 0.0, minStock = 5).matches(low, today))

        assertTrue(product(stock = 0.0).matches(out, today))
        assertFalse(product(stock = 1.0).matches(out, today))
    }

    @Test
    fun `price range is on the purchase price, not the selling price`() {
        val p = product(purchasePrice = 140.0, sellingPrice = 250.0)
        assertTrue(p.matches(ProductListFilters(priceMin = "100", priceMax = "150"), today))
        assertFalse(p.matches(ProductListFilters(priceMin = "200"), today))
        assertFalse(p.matches(ProductListFilters(priceMax = "100"), today))
        // Half-typed or empty bounds do not narrow.
        assertTrue(p.matches(ProductListFilters(priceMin = ".", priceMax = ""), today))
    }

    @Test
    fun `expiring soon means within the next 30 days`() {
        val soon = ProductListFilters(expiringSoon = true)
        assertTrue(product(hasExpiry = 1, expiryDate = "2026-09-13").matches(soon, today))
        assertTrue(product(hasExpiry = 1, expiryDate = "2026-10-13").matches(soon, today))
        assertFalse(product(hasExpiry = 1, expiryDate = "2026-10-14").matches(soon, today))
        assertFalse(product(hasExpiry = 1, expiryDate = "2026-09-12").matches(soon, today))
        assertFalse(product(hasExpiry = 0, expiryDate = "2026-09-20").matches(soon, today))
        assertFalse(product(hasExpiry = 1, expiryDate = "not a date").matches(soon, today))
    }

    // ── combined ─────────────────────────────────────────────────────────────

    @Test
    fun `criteria combine with AND and a price range counts once`() {
        val filters = ProductListFilters(categoryId = 1, unitType = "pièce", priceMin = "100", priceMax = "150")
        assertEquals(3, filters.activeCount)
        assertTrue(product().matches(filters, today))
        assertFalse(product(unitType = "carton").matches(filters, today))
    }
}
