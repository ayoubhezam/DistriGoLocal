package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** A balance is the newest unpaid credit: payments settle the oldest first. */
class SpendByAgeTest {

    private fun spend(balance: Double, vararg credits: Double) = spendByAge(balance, credits)

    @Test
    fun `the balance is taken from the newest credit first`() {
        // 1 000 left on credit in each band, 1 500 still owed: the recent 1 000 and 500 of the month before.
        assertEquals(listOf(1000.0, 500.0, 0.0, 0.0), spend(1500.0, 1000.0, 1000.0, 1000.0, 1000.0))
    }

    @Test
    fun `a balance that covers every credit fills each band`() {
        assertEquals(listOf(100.0, 200.0, 300.0, 400.0), spend(1000.0, 100.0, 200.0, 300.0, 400.0))
    }

    @Test
    fun `what no credit explains is counted as the oldest`() {
        // A supplier's opening balance of 700 and a recent bon of 300 left unpaid.
        assertEquals(listOf(300.0, 0.0, 0.0, 700.0), spend(1000.0, 300.0))
        assertEquals(listOf(0.0, 0.0, 0.0, 250.0), spend(250.0))
    }

    @Test
    fun `a zero balance owes nothing in any band`() {
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), spend(0.0, 500.0, 500.0))
    }
}
