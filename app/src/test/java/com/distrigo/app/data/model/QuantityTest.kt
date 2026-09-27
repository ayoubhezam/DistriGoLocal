package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityTest {

    @Test
    fun normalizeKeepsThreeDecimalsHalfUp() {
        assertEquals(1.25, Quantity.normalize(1.25), 0.0)
        assertEquals(1.001, Quantity.normalize(1.0005), 0.0)
        assertEquals(1.0, Quantity.normalize(1.0004), 0.0)
        assertEquals(0.3, Quantity.normalize(0.1 + 0.2), 0.0)
        assertEquals("no negative zero", "0.0", Quantity.normalize(-0.0001).toString())
    }

    @Test
    fun parseAcceptsTheFrenchComma() {
        assertEquals(2.5, Quantity.parse("2,5")!!, 0.0)
        assertEquals(2.5, Quantity.parse("2.5")!!, 0.0)
        assertEquals(1.25, Quantity.parse(" 1,250 ")!!, 0.0)
        assertEquals(3450.0, Quantity.parse("3 450")!!, 0.0)
        assertEquals(3450.0, Quantity.parse("3 450,00")!!, 0.0)
        assertEquals(1.235, Quantity.parse("1.2345")!!, 0.0)
    }

    @Test
    fun parseRefusesWhatIsNotAQuantity() {
        assertNull(Quantity.parse(""))
        assertNull(Quantity.parse("1,2,3"))
        assertNull(Quantity.parse("1.2.3"))
        assertNull(Quantity.parse("abc"))
        assertNull(Quantity.parse("2kg"))
    }

    @Test
    fun formatDropsTrailingZeros() {
        assertEquals("2", Quantity.format(2.0))
        assertEquals("0.5", Quantity.format(0.5))
        assertEquals("1.25", Quantity.format(1.25))
        assertEquals("1.255", Quantity.format(1.255))
        assertEquals("0.3", Quantity.format(0.1 + 0.2))
        assertEquals("-3", Quantity.format(-3.0))
        assertEquals("0", Quantity.format(-0.0001))
        assertEquals("1000000", Quantity.format(1_000_000.0))
    }

    @Test
    fun sanitizeInputKeepsOneSeparatorAndThreeDecimals() {
        assertEquals("2.5", Quantity.sanitizeInput("2,5"))
        assertEquals("2.53", Quantity.sanitizeInput("2.5.3"))
        assertEquals("1.234", Quantity.sanitizeInput("1.23456"))
        assertEquals("12", Quantity.sanitizeInput("1a2"))
        assertEquals("pièce: digits only", "25", Quantity.sanitizeInput("2,5", allowFractions = false))
    }

    @Test
    fun comparisonsAllowRounding() {
        assertTrue(Quantity.isZero(0.1 + 0.2 - 0.3))
        assertFalse(Quantity.isZero(0.001))
        assertFalse(Quantity.exceeds(0.1 + 0.2, 0.3))
        assertTrue(Quantity.exceeds(0.301, 0.3))
        assertTrue(Quantity.isBelow(0.299, 0.3))
        assertTrue(Quantity.isWhole(3.0000001))
        assertFalse(Quantity.isWhole(2.5))
    }

    @Test
    fun onlyPieceProductsCountWholeUnits() {
        assertTrue(Quantity.allowsFractions("carton"))
        assertFalse(Quantity.allowsFractions("pièce"))
        assertTrue(Quantity.fitsUnit(0.5, "carton"))
        assertFalse(Quantity.fitsUnit(0.5, "pièce"))
        assertTrue(Quantity.fitsUnit(3.0, "pièce"))
    }
}
