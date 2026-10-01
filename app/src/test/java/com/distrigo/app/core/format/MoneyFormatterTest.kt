package com.distrigo.app.core.format

import com.distrigo.app.core.format.MoneyFormat.COMMAS
import com.distrigo.app.core.format.MoneyFormat.DOTS
import com.distrigo.app.core.format.MoneyFormat.SPACES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyFormatterTest {

    /** The printer flavour, whose spaces are plain and so readable in an assertion. */
    private fun fmt(format: MoneyFormat) = MoneyFormatter.printer(format)

    @Test
    fun theThreeFormats() {
        assertEquals("1 236 790,50", fmt(SPACES).amount(1236790.5))
        assertEquals("1,236,790.50", fmt(COMMAS).amount(1236790.5))
        assertEquals("1.236.790,50", fmt(DOTS).amount(1236790.5))
    }

    @Test
    fun spacesIsTheDefault() {
        assertEquals(SPACES, MoneyFormat.DEFAULT)
    }

    @Test
    fun centimesAreAlwaysShown() {
        assertEquals("3 500,00", fmt(SPACES).amount(3500.0))
        assertEquals("0,00", fmt(SPACES).amount(0.0))
        assertEquals("0,05", fmt(SPACES).amount(0.05))
        assertEquals("12,50", fmt(SPACES).amount(12.5))
        assertEquals("3,500.00", fmt(COMMAS).amount(3500.0))
        assertEquals("3.500,00", fmt(DOTS).amount(3500.0))
    }

    @Test
    fun groupingStartsAtAThousand() {
        val f = fmt(SPACES)
        assertEquals("1,00", f.amount(1.0))
        assertEquals("99,00", f.amount(99.0))
        assertEquals("999,00", f.amount(999.0))
        assertEquals("1 000,00", f.amount(1000.0))
        assertEquals("10 000,00", f.amount(10_000.0))
        assertEquals("100 000,00", f.amount(100_000.0))
        assertEquals("1 000 000,00", f.amount(1_000_000.0))
        assertEquals("999 999 999 999,99", f.amount(999_999_999_999.99))
        assertEquals("1.000.000.000,00", fmt(DOTS).amount(1e9))
    }

    @Test
    fun roundsHalfUpToTheCentime() {
        val f = fmt(SPACES)
        assertEquals("1,01", f.amount(1.005)) // a double's 1.005 is 1.00499…; read as typed, it rounds up
        assertEquals("2,68", f.amount(2.675))
        assertEquals("1 000,00", f.amount(999.995))
        assertEquals("0,33", f.amount(1.0 / 3))
        assertEquals("0,67", f.amount(2.0 / 3))
        assertEquals("0,30", f.amount(0.1 + 0.2))
    }

    @Test
    fun negatives() {
        assertEquals("-1 250,00", fmt(SPACES).amount(-1250.0))
        assertEquals("-1,250.75", fmt(COMMAS).amount(-1250.75))
        assertEquals("-1.250,75", fmt(DOTS).amount(-1250.75))
        assertEquals("-999,00", fmt(SPACES).amount(-999.0))
        assertEquals("-0,01", fmt(SPACES).amount(-0.005))
    }

    @Test
    fun neverMinusZero() {
        assertEquals("0,00", fmt(SPACES).amount(-0.0))
        assertEquals("0,00", fmt(SPACES).amount(-0.004))
        assertEquals("0,00 DA", fmt(SPACES).signedDa(-0.004))
    }

    @Test
    fun withTheCurrency() {
        assertEquals("1 236 790,50 DA", fmt(SPACES).da(1236790.5))
        assertEquals("1,236,790.50 DA", fmt(COMMAS).da(1236790.5))
        assertEquals("1.236.790,50 DA", fmt(DOTS).da(1236790.5))
        assertEquals("-12,00 DA", fmt(SPACES).da(-12.0))
    }

    @Test
    fun signedForAMovement() {
        assertEquals("+1 250,00 DA", fmt(SPACES).signedDa(1250.0))
        assertEquals("-1 250,00 DA", fmt(SPACES).signedDa(-1250.0))
        assertEquals("0,00 DA", fmt(SPACES).signedDa(0.0))
        assertEquals("+0,01 DA", fmt(SPACES).signedDa(0.005))
        assertEquals("+1,250.00 DA", fmt(COMMAS).signedDa(1250.0))
    }

    @Test
    fun notANumberNeverPrintsAsNaN() {
        assertEquals("—", fmt(SPACES).amount(Double.NaN))
        assertEquals("—", fmt(SPACES).amount(Double.POSITIVE_INFINITY))
        assertEquals("— DA", fmt(SPACES).da(Double.NEGATIVE_INFINITY))
        assertEquals("— DA", fmt(SPACES).signedDa(Double.NaN))
    }

    @Test
    fun theScreenUsesNoBreakSpacesThePrinterPlainOnes() {
        val nb = ' '
        assertEquals("1${nb}236${nb}790,50${nb}DA", MoneyFormatter.screen(SPACES).da(1236790.5))
        assertEquals("1,236,790.50${nb}DA", MoneyFormatter.screen(COMMAS).da(1236790.5))
        val printed = MoneyFormatter.printer(SPACES).da(1236790.5)
        assertTrue("printer output must be ASCII: $printed", printed.all { it.code < 128 })
        for (format in MoneyFormat.entries) {
            val text = MoneyFormatter.printer(format).signedDa(-1236790.5)
            assertTrue("$format printer output must be ASCII: $text", text.all { it.code < 128 })
        }
    }

    @Test
    fun thePhonesLanguageChangesNothing() {
        val saved = Locale.getDefault()
        try {
            val outputs = listOf(Locale.FRANCE, Locale.US, Locale.GERMANY, Locale("ar", "DZ")).map { locale ->
                Locale.setDefault(locale)
                MoneyFormat.entries.map { MoneyFormatter.screen(it).da(-1236790.5) }
            }
            assertEquals(1, outputs.distinct().size)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun oneInstancePerFormatAndFlavour() {
        assertSame(MoneyFormatter.screen(DOTS), MoneyFormatter.screen(DOTS))
        assertSame(MoneyFormatter.printer(DOTS), MoneyFormatter.printer(DOTS))
        assertNotSame(MoneyFormatter.screen(DOTS), MoneyFormatter.printer(DOTS))
        assertNotSame(MoneyFormatter.screen(DOTS), MoneyFormatter.screen(COMMAS))
    }

    @Test
    fun storedKeysArePinned() {
        // These strings are in users' databases: changing one silently resets their choice.
        assertEquals("spaces", SPACES.key)
        assertEquals("commas", COMMAS.key)
        assertEquals("dots", DOTS.key)
        for (format in MoneyFormat.entries) assertEquals(format, MoneyFormat.fromKey(format.key))
    }

    @Test
    fun anUnknownKeyIsTheDefault() {
        assertEquals(SPACES, MoneyFormat.fromKey(null))
        assertEquals(SPACES, MoneyFormat.fromKey(""))
        assertEquals(SPACES, MoneyFormat.fromKey("SPACES")) // the enum name is not the key
        assertEquals(SPACES, MoneyFormat.fromKey("apostrophes")) // a format from a newer version
    }

    @Test
    fun theDecimalMarkIsNeverAlsoTheThousandsMark() {
        for (format in MoneyFormat.entries) assertTrue(format.toString(), format.thousands != format.decimal)
    }
}
