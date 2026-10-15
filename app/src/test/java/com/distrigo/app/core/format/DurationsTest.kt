package com.distrigo.app.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lengths as a merchant says them: exact for a code's chosen days, rounded for a period between two dates. */
class DurationsTest {

    @Test
    fun `a code's days read as the presets, then as months, years or days`() {
        assertEquals("1 mois", subscriptionLength(30))
        assertEquals("3 mois", subscriptionLength(90))
        assertEquals("6 mois", subscriptionLength(182))
        assertEquals("1 an", subscriptionLength(365))
        assertEquals("2 mois", subscriptionLength(60))
        assertEquals("2 ans", subscriptionLength(730))
        assertEquals("3 ans", subscriptionLength(1095))
        assertEquals("45 jours", subscriptionLength(45))
        assertEquals("15 jours", subscriptionLength(15))
        assertEquals("1 jour", subscriptionLength(1))
        assertEquals("1100 jours", subscriptionLength(1100))
    }

    @Test
    fun `a period between two dates rounds to what it is near`() {
        assertEquals("30 jours", approximateLength(30))
        assertEquals("45 jours", approximateLength(45))
        assertEquals("6 mois", approximateLength(182))
        assertEquals("6 mois", approximateLength(184))
        assertEquals("1 an", approximateLength(365))
        assertEquals("1 an", approximateLength(380))
        assertEquals("2 ans", approximateLength(731))
        assertEquals("18 mois", approximateLength(547))
    }
}
