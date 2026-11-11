package com.distrigo.app.data.model

import com.distrigo.app.data.model.PaymentAllocation.Credit
import org.junit.Assert.assertEquals
import org.junit.Test

/** A party's balance spent on its documents, newest first: later payments settle the oldest documents first (REP-02). */
class PaymentAllocationTest {

    // Newest first: sale 3 left 500 unpaid, sale 2 left 800, sale 1 left 1 000
    private val credits = listOf(Credit(3, 500.0), Credit(2, 800.0), Credit(1, 1000.0))

    @Test
    fun aBalanceSettledInFullLeavesNothingOwed() {
        assertEquals(emptyMap<Int, Double>(), PaymentAllocation.spend(0.0, credits))
        assertEquals(emptyMap<Int, Double>(), PaymentAllocation.spend(-200.0, credits))   // paid ahead
    }

    @Test
    fun aPaymentSettlesTheOldestFirst() {
        // 2 300 owed, 1 000 paid: the oldest sale is settled, the two newer still owe in full
        assertEquals(mapOf(3 to 500.0, 2 to 800.0), PaymentAllocation.spend(1300.0, credits))
        // 1 500 paid: the oldest settled, the middle one partly
        assertEquals(mapOf(3 to 500.0, 2 to 300.0), PaymentAllocation.spend(800.0, credits))
    }

    @Test
    fun aBalanceNoDocumentExplainsLeavesEachItsOwnCredit() {
        // An opening balance of 700 on top of 2 300: every document still owes its own credit
        assertEquals(mapOf(3 to 500.0, 2 to 800.0, 1 to 1000.0), PaymentAllocation.spend(3000.0, credits))
    }

    @Test
    fun toTheCentime() {
        assertEquals(mapOf(3 to 0.01), PaymentAllocation.spend(0.014, credits))
        assertEquals(emptyMap<Int, Double>(), PaymentAllocation.spend(0.004, credits))
    }
}
