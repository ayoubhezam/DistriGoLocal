package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductUnitTest {

    @Test
    fun kgIsWeighedToTheGram() {
        assertTrue(Quantity.allowsFractions(ProductUnit.KG))
        assertTrue(Quantity.fitsUnit(1.25, ProductUnit.KG))
        assertEquals(Quantity.STEP, Quantity.smallest(ProductUnit.KG), 0.0)
        assertFalse("kg is bought as one weight, not by the colis", ProductUnit.isBoughtByColis(ProductUnit.KG))
        assertTrue(ProductUnit.isBoughtByColis(ProductUnit.PIECE))
    }

    @Test
    fun labelsAndPlurals() {
        assertEquals(listOf("Carton", "Pièce", "Kg"), ProductUnit.ALL.map(ProductUnit::label))
        assertEquals("kg", ProductUnit.plural(ProductUnit.KG, 15.75))
        assertEquals("cartons", ProductUnit.plural(ProductUnit.CARTON, 3.0))
        assertEquals("carton", ProductUnit.plural(ProductUnit.CARTON, 0.5))
        assertEquals("pièces", ProductUnit.plural(ProductUnit.PIECE, 12.0))
    }
}
