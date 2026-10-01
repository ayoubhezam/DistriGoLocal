package com.distrigo.app.data.importer

import com.distrigo.app.data.importer.ImportNumber.Reading
import org.junit.Assert.assertEquals
import org.junit.Test

/** Numbers typed as text in a workbook: every separator style read, nothing read a thousand times too small. */
class ImportNumberTest {

    private fun value(typed: String): Double =
        (ImportNumber.read(typed) as? Reading.Value)?.value ?: throw AssertionError("« $typed » should read as a number, was ${ImportNumber.read(typed)}")

    private fun assertReads(expected: Double, typed: String) = assertEquals("« $typed »", expected, value(typed), 0.0)
    private fun assertAmbiguous(typed: String) = assertEquals("« $typed »", Reading.Ambiguous, ImportNumber.read(typed))
    private fun assertRefused(typed: String) = assertEquals("« $typed »", Reading.NotANumber, ImportNumber.read(typed))

    @Test
    fun plainNumbers() {
        assertReads(3450.0, "3450")
        assertReads(3450.5, "3450.5")
        assertReads(3450.5, "3450,5")
        assertReads(0.5, "0,5")
        assertReads(0.5, ".5")
        assertReads(7.0, "007")
        assertReads(-12.5, "-12,5")
        assertReads(12.5, "+12.5")
        assertReads(1234.5, "  1234,50  ")
    }

    @Test
    fun spacesAsThousands() {
        assertReads(3450.0, "3 450,00")
        assertReads(1236790.5, "1 236 790,50")
        assertReads(1236790.5, "1 236 790.50")
        assertReads(1236790.0, "1 236 790")
        // No-break, narrow no-break (what French Excel and Android write) and thin spaces.
        assertReads(1236790.5, "1 236 790,50")
        assertReads(1236790.5, "1 236 790,50")
        assertReads(1236790.5, "1 236 790,50")
        assertReads(-3450.0, "-3 450,00")
    }

    @Test
    fun commasAsThousands() {
        assertReads(1236790.5, "1,236,790.50")
        assertReads(1236790.0, "1,236,790")
        assertReads(1500.25, "1,500.25")
    }

    @Test
    fun dotsAsThousands() {
        assertReads(1236790.5, "1.236.790,50")
        assertReads(1236790.0, "1.236.790")
        assertReads(1500.25, "1.500,25")
    }

    @Test
    fun aLoneMarkBeforeThreeDigitsCouldBeEither() {
        // The old reading took all of these for 1.5 or 12.75 — a thousand times too small.
        assertAmbiguous("1,500")
        assertAmbiguous("1.500")
        assertAmbiguous("12.750")
        assertAmbiguous("999,000")
        assertAmbiguous("-1,500")
    }

    @Test
    fun aLoneMarkThatCanOnlyBeDecimal() {
        assertReads(0.5, "0,500") // no thousands separator follows a lone zero
        assertReads(1234.5, "1234,500") // four digits are already too many for a first group
        assertReads(1.5, "1,50")
        assertReads(1.5005, "1,5005")
    }

    @Test
    fun malformedGroupsAreRefused() {
        assertRefused("12 34")
        assertRefused("1 2345")
        assertRefused("1234 567")
        assertRefused("1,23,456.00") // Indian grouping: not one of ours
        assertRefused("1.5.0")
        assertRefused("12,5,0")
        assertRefused("1,236.790,50") // the decimal mark used twice
        assertRefused("1 236.790,50") // two different thousands separators
    }

    @Test
    fun whatIsNotANumberAtAll() {
        assertRefused("")
        assertRefused("   ")
        assertRefused("abc")
        assertRefused("12 DA")
        assertRefused("12,")
        assertRefused("-")
        assertRefused(",")
        assertRefused("1e5")
        assertRefused("NaN")
        assertRefused("Infinity")
        assertRefused("- 5")
        assertRefused("1,5 0")
    }

    @Test
    fun whatTheAppItselfExportsReadsBackOrIsRefused() {
        // CsvWriter writes amounts ungrouped, with a decimal comma: always two decimals, never ambiguous.
        assertReads(3450.0, "3450,00")
        assertReads(0.5, "0,50")
        // A quantity to three decimals left as text is: 12,125 cartons or 12 125? Refused, never guessed.
        // Opened in French Excel the cell is a number, and numeric cells never come here.
        assertAmbiguous("12,125")
        assertReads(1234.125, "1234,125")
    }
}
