package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun aBalanceReadsFromHalfACentime() {
        // A remainder of the sums' decimals is settled, not owed: 0.1 + 0.2 - 0.3 is 5.55e-17, not 0
        assertTrue(Amount.settled(0.1 + 0.2 - 0.3))
        assertTrue(Amount.settled(0.004))
        assertTrue(Amount.settled(-0.004))
        assertTrue(Amount.owing(0.005))
        assertTrue(Amount.owing(0.01))
        assertTrue(Amount.inAdvance(-0.005))
        assertFalse(Amount.owing(0.004))
        assertFalse(Amount.inAdvance(-0.004))
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
}
