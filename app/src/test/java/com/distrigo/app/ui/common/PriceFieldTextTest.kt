package com.distrigo.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** A cart's « Prix unitaire » keeps what is typed, and is written afresh only for a price set from outside. */
class PriceFieldTextTest {

    /** Deleting « 700.00 » from the end, then typing 800: each step stays as typed (it was « 0.00 », then 8000.00). */
    @Test
    fun whatIsTypedStaysAsTyped() {
        for ((typed, price) in listOf("700.0" to 700.0, "70" to 70.0, "7" to 7.0, "" to 0.0, "8" to 8.0, "80" to 80.0, "800" to 800.0)) {
            assertEquals(typed, priceFieldText(typed, price))
        }
        // A decimal on its way in
        assertEquals("120.", priceFieldText("120.", 120.0))
        assertEquals("120.5", priceFieldText("120.5", 120.5))
    }

    /** A past price picked, or another line opened: the field shows that price. */
    @Test
    fun aPriceSetFromOutsideIsWrittenAfresh() {
        assertEquals("650.00", priceFieldText("800", 650.0))
        assertEquals("650.00", priceFieldText("", 650.0))
        assertEquals("0.00", priceFieldText("12", 0.0))
    }
}
