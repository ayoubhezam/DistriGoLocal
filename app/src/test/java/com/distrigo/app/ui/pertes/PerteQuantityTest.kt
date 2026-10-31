package com.distrigo.app.ui.pertes

import com.distrigo.app.R
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import com.distrigo.app.ui.common.quantityText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the perte dialog lets through: under strict stock, no more than the dépôt holds. */
class PerteQuantityTest {

    @Test
    fun `under strict stock a loss is capped at the depot stock`() {
        assertNull(perteQuantityError(5.0, cap = 5.0, unit = "pièce"))
        assertEquals(stockShort(5.0, "pièce"), perteQuantityError(6.0, cap = 5.0, unit = "pièce"))
    }

    @Test
    fun `with negative stock allowed there is no cap`() {
        assertNull(perteQuantityError(500.0, cap = null, unit = "pièce"))
    }

    @Test
    fun `nothing, zero or less is not a loss`() {
        assertEquals(invalid, perteQuantityError(null, cap = null, unit = "pièce"))
        assertEquals(invalid, perteQuantityError(0.0, cap = 5.0, unit = "pièce"))
    }

    // The words are resources now (Phase 4): the test checks which message is given, not its French.
    private val invalid = AppStrings.write(UiText.Res(R.string.common_invalid_quantity))

    private fun stockShort(cap: Double, unit: String) =
        AppStrings.write(UiText.Res(R.string.pertes_stock_short, AppStrings.write(quantityText(cap, unit))))
}
