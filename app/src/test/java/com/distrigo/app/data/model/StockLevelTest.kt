package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockLevelTest {

    @Test
    fun everyStockIsInExactlyOneBand() {
        for (stock in listOf(-5.0, 0.0, 0.0004, 0.001, 0.5, 1.0, 4.999, 5.0, 5.0004, 5.001, 99.5)) {
            val bands = listOf(StockLevel.IN_STOCK, StockLevel.LOW_STOCK, StockLevel.OUT_OF_STOCK)
                .count { StockLevel.matches(it, stock, 5.0) }
            assertEquals("stock $stock", 1, bands)
        }
    }

    @Test
    fun halfACartonUnderItsMinimumIsLow() {
        assertEquals(StockLevel.LOW_STOCK, StockLevel.of(0.5, 2.0))
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.of(0.1 + 0.2 - 0.3, 2.0))
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.of(-1.0, 2.0))
        assertEquals(StockLevel.LOW_STOCK, StockLevel.of(2.0, 2.0))
        assertEquals(StockLevel.IN_STOCK, StockLevel.of(2.5, 2.0))
        assertEquals("no minimum: any stock is in stock", StockLevel.IN_STOCK, StockLevel.of(0.5, 0.0))
    }

    @Test
    fun anUnknownLevelFiltersNothing() {
        assertTrue(StockLevel.matches("weird", -3.0, 5.0))
        assertEquals(null, StockLevel.sql("weird", "p.stock", "p.min_stock"))
        assertFalse(StockLevel.matches(StockLevel.IN_STOCK, 0.0, 0.0))
    }
}
