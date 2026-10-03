package com.distrigo.app.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a sale line's price is said to be below the product's purchase price. */
class BelowCostTest {

    @Test
    fun `a price under the purchase price is below cost`() {
        // The tomato concentrate sold at 1 000 DA, bought at 1 446 DA.
        assertTrue(isBelowCost(1000.0, 1446.0))
        assertTrue(isBelowCost(1445.99, 1446.0))
    }

    @Test
    fun `a price at or above the purchase price is not`() {
        assertFalse(isBelowCost(1446.0, 1446.0))
        assertFalse(isBelowCost(1735.2, 1446.0))
        // Half a centime of rounding is not a loss.
        assertFalse(isBelowCost(1445.996, 1446.0))
    }

    @Test
    fun `a product with no purchase price entered is never below cost`() {
        assertFalse(isBelowCost(10.0, 0.0))
        assertFalse(isBelowCost(0.0, 0.0))
    }
}
