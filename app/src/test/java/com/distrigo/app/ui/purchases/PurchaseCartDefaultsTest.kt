package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseCartDefaultsTest {

    private fun product(unitType: String, packSize: Int) = Product(
        id = 1, name = "Thé rouge 500g", barcode = null,
        selling_price = 250.0, purchase_price = 200.0,
        stock = 0.0, min_stock = 0, unit_type = unitType,
        packages = 0, pack_size = packSize,
        has_expiry = 0, expiry_date = null, image_uri = null,
        category_name = null, category_id = null,
        supplier_name = null, supplier_id = null
    )

    // ── pièce ────────────────────────────────────────────────────────────────

    @Test
    fun `piece with a stated pack size starts at one full colis`() {
        val item = newPurchaseCartItem(product("pièce", packSize = 30))

        assertEquals(1.0, item.nbColis, 0.0)
        assertEquals(30, item.uniteParColis)
        assertEquals(30.0, item.quantity, 0.0)
        assertEquals(200.0, item.unitCost, 0.0)
    }

    @Test
    fun `piece with pack size 0 falls back to 1 instead of a zero line`() {
        val item = newPurchaseCartItem(product("pièce", packSize = 0))

        assertEquals(1, item.uniteParColis)
        assertEquals(1.0, item.quantity, 0.0)
    }

    @Test
    fun `piece with a negative pack size falls back to 1`() {
        val item = newPurchaseCartItem(product("pièce", packSize = -5))

        assertEquals(1, item.uniteParColis)
        assertEquals(1.0, item.quantity, 0.0)
    }

    @Test
    fun `plus adds another colis of pieces and minus takes it back`() {
        val one = newPurchaseCartItem(product("pièce", packSize = 30))

        val two = one.withNbColis(one.nbColis + 1.0)
        assertEquals(2.0, two.nbColis, 0.0)
        assertEquals(60.0, two.quantity, 0.0)

        val back = two.withNbColis(two.nbColis - 1.0)
        assertEquals(30.0, back.quantity, 0.0)
    }

    @Test
    fun `editing units per colis recomputes from the current colis count`() {
        val twoColis = newPurchaseCartItem(product("pièce", packSize = 30)).withNbColis(2.0)

        val edited = twoColis.withUniteParColis(24)
        assertEquals(24, edited.uniteParColis)
        assertEquals(48.0, edited.quantity, 0.0)
    }

    @Test
    fun `a per-bon packaging never changes the product`() {
        val edited = newPurchaseCartItem(product("pièce", packSize = 30)).withUniteParColis(50)

        assertEquals(30, edited.product.pack_size)
    }

    // ── carton: exactly what the two call sites built before ─────────────────

    @Test
    fun `carton is unchanged and ignores pack size`() {
        for (packSize in listOf(0, 30)) {
            val p = product("carton", packSize)
            val before = CartItem(
                product       = p,
                quantity      = 1.0,
                unitCost      = p.purchase_price,
                nbColis       = 1.0,
                uniteParColis = 1
            )
            assertEquals(before, newPurchaseCartItem(p))
        }
    }
}
