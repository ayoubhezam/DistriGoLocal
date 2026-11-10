package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a product's cost follows a receipt, for each method (FIN-04). */
class CostMethodTest {

    /** The settings example: 100 held at 50, 100 received at 60. */
    @Test
    fun theSettingsExample() {
        assertEquals(55.0, CostMethod.WEIGHTED_AVERAGE.costAfter(100.0, 50.0, 100.0, 60.0), 0.0)
        assertEquals(60.0, CostMethod.LAST_PURCHASE.costAfter(100.0, 50.0, 100.0, 60.0), 0.0)
        assertEquals(50.0, CostMethod.MANUAL.costAfter(100.0, 50.0, 100.0, 60.0), 0.0)
    }

    /** Weighted by quantity, to the centime: 30 at 100 and 10 at 120 is 105; 3 at 10 and 1 at 11 is 10,25. */
    @Test
    fun theAverageIsWeightedAndRoundedToTheCentime() {
        assertEquals(105.0, CostMethod.WEIGHTED_AVERAGE.costAfter(30.0, 100.0, 10.0, 120.0), 0.0)
        assertEquals(10.25, CostMethod.WEIGHTED_AVERAGE.costAfter(3.0, 10.0, 1.0, 11.0), 0.0)
        // 2 at 10 and 1 at 11: 10,333… → 10,33
        assertEquals(10.33, CostMethod.WEIGHTED_AVERAGE.costAfter(2.0, 10.0, 1.0, 11.0), 0.0)
        // Half a carton counts as half
        assertEquals(52.0, CostMethod.WEIGHTED_AVERAGE.costAfter(2.0, 50.0, 0.5, 60.0), 0.0)
    }

    /** With no stock to value — none held, below zero, or a cost never set — the goods received are their own cost. */
    @Test
    fun withNothingToAverageTheReceiptIsTheCost() {
        assertEquals(60.0, CostMethod.WEIGHTED_AVERAGE.costAfter(0.0, 50.0, 100.0, 60.0), 0.0)
        assertEquals(60.0, CostMethod.WEIGHTED_AVERAGE.costAfter(-5.0, 50.0, 100.0, 60.0), 0.0)
        assertEquals(60.0, CostMethod.WEIGHTED_AVERAGE.costAfter(100.0, 0.0, 100.0, 60.0), 0.0)
        // Nothing received moves nothing
        assertEquals(50.0, CostMethod.WEIGHTED_AVERAGE.costAfter(100.0, 50.0, 0.0, 60.0), 0.0)
    }

    @Test
    fun anUnknownKeyIsTheDefault() {
        assertEquals(CostMethod.WEIGHTED_AVERAGE, CostMethod.DEFAULT)
        assertEquals(CostMethod.LAST_PURCHASE, CostMethod.fromKey("last"))
        assertEquals(CostMethod.MANUAL, CostMethod.fromKey("manual"))
        assertEquals(CostMethod.DEFAULT, CostMethod.fromKey(null))
        assertEquals(CostMethod.DEFAULT, CostMethod.fromKey("fifo"))
    }
}
