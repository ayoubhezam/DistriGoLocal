package com.distrigo.app.ui.pertes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the perte dialog lets through: under strict stock, no more than the dépôt holds. */
class PerteQuantityTest {

    @Test
    fun `under strict stock a loss is capped at the depot stock`() {
        assertNull(perteQuantityError(5.0, cap = 5.0, unit = "pièce"))
        assertEquals("Stock insuffisant : 5 pièce au dépôt", perteQuantityError(6.0, cap = 5.0, unit = "pièce"))
    }

    @Test
    fun `with negative stock allowed there is no cap`() {
        assertNull(perteQuantityError(500.0, cap = null, unit = "pièce"))
    }

    @Test
    fun `nothing, zero or less is not a loss`() {
        assertEquals("Quantité invalide", perteQuantityError(null, cap = null, unit = "pièce"))
        assertEquals("Quantité invalide", perteQuantityError(0.0, cap = 5.0, unit = "pièce"))
    }
}
