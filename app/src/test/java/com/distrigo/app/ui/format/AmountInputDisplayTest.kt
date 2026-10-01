package com.distrigo.app.ui.format

import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormat.COMMAS
import com.distrigo.app.core.format.MoneyFormat.DOTS
import com.distrigo.app.core.format.MoneyFormat.SPACES
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an amount field shows while it is typed in, and where its cursor goes. */
class AmountInputDisplayTest {

    private val nb = ' '

    private fun shown(raw: String, format: MoneyFormat) = AmountInputDisplay.of(raw, format).text

    @Test
    fun eachFormatGroupsAndMarksTheDecimal() {
        assertEquals("1${nb}236${nb}790,5", shown("1236790.5", SPACES))
        assertEquals("1,236,790.5", shown("1236790.5", COMMAS))
        assertEquals("1.236.790,5", shown("1236790.5", DOTS))
    }

    @Test
    fun whatIsTypedIsShownAsTyped() {
        // No centimes added while typing: "12." is a decimal mark waiting for its digits.
        assertEquals("12,", shown("12.", SPACES))
        assertEquals("12.", shown("12.", COMMAS))
        assertEquals("0,5", shown("0.5", DOTS))
        assertEquals("999", shown("999", COMMAS))
        assertEquals("1,000", shown("1000", COMMAS))
        assertEquals("", shown("", SPACES))
        // Digits after the mark are never grouped.
        assertEquals("1.000,12", shown("1000.12", DOTS))
    }

    @Test
    fun theCursorSkipsInsertedMarksButNotTheDecimal() {
        // raw "1234.5" -> "1,234.5" in COMMAS: one ',' inserted at 1; the '.' is the typed one.
        val d = AmountInputDisplay.of("1234.5", COMMAS)
        assertEquals("1,234.5", d.text)
        assertArrayEquals(intArrayOf(0, 2, 3, 4, 5, 6, 7), d.toShown)
        // Shown position 1 (before the inserted ',') and 2 (after it) are both raw position 1.
        assertArrayEquals(intArrayOf(0, 1, 1, 2, 3, 4, 5, 6), d.toRaw)
    }

    /** The mark that is inserted can be the very character a DOTS user types as a decimal elsewhere. */
    @Test
    fun aThousandsMarkThatLooksLikeADecimalIsStillSkipped() {
        val d = AmountInputDisplay.of("1234567", DOTS)
        assertEquals("1.234.567", d.text)
        for (raw in 0.."1234567".length) assertEquals(raw, d.toRaw[d.toShown[raw]])
    }

    @Test
    fun everyRawPositionRoundTrips() {
        for (format in MoneyFormat.entries) for (raw in listOf("", "5", "12.", "1000", "1234567.89", "0.05", "100000")) {
            val d = AmountInputDisplay.of(raw, format)
            for (i in 0..raw.length) assertEquals("$format «$raw» at $i", i, d.toRaw[d.toShown[i]])
            assertEquals(d.text.length, d.toShown[raw.length])
            assertEquals(raw.length, d.toRaw[d.text.length])
        }
    }
}
