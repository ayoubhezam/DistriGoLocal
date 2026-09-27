package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountTest {

    @Test
    fun parseReadsTheFrenchComma() {
        assertEquals(3500.5, Amount.parse("3500,5")!!, 0.0)
        assertEquals(3500.5, Amount.parse("3 500.50")!!, 0.0)
        assertEquals(1250.13, Amount.parse("1250.125")!!, 0.0)
        assertNull(Amount.parse(""))
        assertNull(Amount.parse("12,5,0"))
        assertNull(Amount.parse("abc"))
    }

    @Test
    fun sanitizeKeepsOneSeparatorAndTwoDecimals() {
        assertEquals("3500.5", Amount.sanitizeInput("3500,5"))
        assertEquals("12.34", Amount.sanitizeInput("12.345"))
        assertEquals("12.34", Amount.sanitizeInput("12.3.4"))
        assertEquals("7", Amount.sanitizeInput("007"))
        assertEquals("0.5", Amount.sanitizeInput(",5"))
        assertEquals("0", Amount.sanitizeInput("0"))
        assertEquals("", Amount.sanitizeInput(""))
        assertEquals("", Amount.sanitizeInput("abc"))
    }

    @Test
    fun formatGroupsThousandsAndShowsCentsOnlyWhenThereAreSome() {
        assertEquals("3 500", Amount.format(3500.0))
        assertEquals("1 250.50", Amount.format(1250.5))
        assertEquals("999", Amount.format(999.0))
        assertEquals("1 000 000", Amount.format(1_000_000.0))
        assertEquals("0.05", Amount.format(0.05))
        assertEquals("3 500.5", Amount.groupThousands("3500.5"))
        assertEquals("12", Amount.groupThousands("12"))
    }
}
