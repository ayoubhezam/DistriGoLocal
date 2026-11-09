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
    fun anArabicKeyboardsDigitsAreReadAsLatin() {
        // What the field keeps, and so shows: 0–9 and a '.'
        assertEquals("1250.5", Amount.sanitizeInput("١٢٥٠٫٥"))
        assertEquals("12500", Amount.sanitizeInput("١٢٬٥٠٠"))
        assertEquals("3.5", Amount.sanitizeInput("٣,٥"))
        assertEquals(1250.5, Amount.parse("١٢٥٠٫٥")!!, 0.0)
        assertEquals(12500.0, Amount.parse("١٢٬٥٠٠")!!, 0.0)
        assertEquals(3500.5, Amount.parse("۳۵۰۰,۵")!!, 0.0)
    }

    @Test
    fun aSavedAmountSeedsItsFieldAsPlainDigits() {
        assertEquals("10000000", Amount.inputText(1.0E7))
        assertEquals("100000", Amount.inputText(100000.0))
        assertEquals("2.5", Amount.inputText(2.50))
        assertEquals("0.1", Amount.inputText(0.1))
        assertEquals("1250.13", Amount.inputText(1250.125))
        assertEquals("0", Amount.inputText(0.0))
        assertEquals("", Amount.inputText(Double.NaN))
        // What the field holds reads back as the saved amount
        assertEquals(1.0E7, Amount.parse(Amount.inputText(1.0E7))!!, 0.0)
        assertEquals(Amount.inputText(123456.7), Amount.sanitizeInput(Amount.inputText(123456.7)))
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
    fun aPaidAmountTypedWithTheFrenchCommaIsReadAsTyped() {
        // FIN-01: the field used to drop the ',' and keep « 150050 »; it now keeps « 1500.50 »
        assertEquals("1500.50", Amount.sanitizeInput("1500,50"))
        assertEquals(1500.5, Amount.paidOrNull(Amount.sanitizeInput("1500,50"))!!, 0.0)
        assertEquals(1500.5, Amount.paidOrNull("1500,50")!!, 0.0)
        // Blank is nothing paid; text that is not an amount is refused rather than read as 0
        assertEquals(0.0, Amount.paidOrNull("")!!, 0.0)
        assertEquals(0.0, Amount.paidOrNull("  ")!!, 0.0)
        assertNull(Amount.paidOrNull("1.500.00"))
        assertNull(Amount.paidOrNull("1500,50,00"))
        // What « Tout réglé » and an edited document put in the field reads back unchanged
        assertEquals(450.45, Amount.paidOrNull(String.format(java.util.Locale.ROOT, "%.2f", 450.45))!!, 0.0)
        assertEquals(1.0E7, Amount.paidOrNull("10000000.00")!!, 0.0)
    }

    @Test
    fun paymentAboveTheTotalIsAnAdvanceOnlyFromHalfACentime() {
        // 3 × 150,15 is 450.45000000000005: paid 450.45 is settled, not owing and not an advance
        val total = 3 * 150.15
        assertTrue(Amount.settled(total - 450.45))
        assertNull(Amount.advanceOf(total, 450.45))
        // The old « 1500,50 » typo: 150 050 paid on 1 500,50 is an advance, which the forms now ask about
        assertEquals(148549.5, Amount.advanceOf(1500.5, 150050.0)!!, 1e-9)
        assertEquals(0.01, Amount.advanceOf(100.0, 100.01)!!, 1e-9)
        assertNull(Amount.advanceOf(1500.5, 1500.5))
        assertNull(Amount.advanceOf(1500.5, 1000.0))
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
