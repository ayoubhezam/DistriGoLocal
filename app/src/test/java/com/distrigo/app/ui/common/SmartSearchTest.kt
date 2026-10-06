package com.distrigo.app.ui.common

import com.distrigo.app.data.local.dao.searchSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's one way of searching: every word, in any order, ignoring case. */
class SmartSearchTest {

    @Test
    fun `every word must appear, in any field, in any order`() {
        val tokens = searchTokens("  bl  HAR 25 ")
        assertTrue(matchesAllTokens(tokens, "Haricots blancs Amor Benamor 250g"))
        assertTrue(matchesAllTokens(tokens, "Haricots", null, "blancs 250g"))
        assertFalse(matchesAllTokens(tokens, "Haricots rouges 250g"))
        assertTrue(matchesAllTokens(searchTokens(""), "anything"))
    }

    @Test
    fun `a Room query gets three word slots, the rest kept as one phrase`() {
        assertEquals(listOf("", "", ""), searchSlots("   "))
        assertEquals(listOf("fac", "", ""), searchSlots("fac"))
        assertEquals(listOf("fac", "12", ""), searchSlots(" fac  12 "))
        assertEquals(listOf("fac", "12", "mars 2026"), searchSlots("fac 12 mars 2026"))
    }
}
