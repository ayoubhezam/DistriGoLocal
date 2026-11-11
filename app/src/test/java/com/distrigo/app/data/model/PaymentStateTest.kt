package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentStateTest {

    @Test
    fun settledToTheHalfCentimeIsPaid() {
        // 3 × 150,15 stored as 450.45000000000005, « Tout réglé » paid 450.45
        assertEquals(PaymentState.PAID, PaymentState.of(3 * 150.15, 450.45))
        // 2,675 × 3 stored before FIN-05 as 8.024999999999999: 8.02 paid leaves less than half a centime
        assertEquals(PaymentState.PAID, PaymentState.of(3 * 2.675, 8.02))
        // Half a centime or more is owed, as in Amount.owing
        assertEquals(PaymentState.PARTIAL, PaymentState.of(8.03, 8.02))
        assertEquals(PaymentState.PAID, PaymentState.of(1000.0, 999.996))
        assertEquals(PaymentState.PARTIAL, PaymentState.of(1000.0, 999.99))
    }

    @Test
    fun aTotalOfZeroIsPaid() {
        // Owner's D2: nothing to pay is « Payée », as the Dashboard already said
        assertEquals(PaymentState.PAID, PaymentState.of(0.0, 0.0))
        assertEquals(PaymentState.PAID, PaymentState.of(0.004, 0.0))
    }

    @Test
    fun paidAheadIsPaid() {
        assertEquals(PaymentState.PAID, PaymentState.of(1500.5, 150050.0))
    }

    @Test
    fun unpaidAndPartialMeetAtHalfACentimePaid() {
        assertEquals(PaymentState.UNPAID, PaymentState.of(1000.0, 0.0))
        assertEquals(PaymentState.UNPAID, PaymentState.of(1000.0, 0.004))
        assertEquals(PaymentState.PARTIAL, PaymentState.of(1000.0, 0.005))
        assertEquals(PaymentState.PARTIAL, PaymentState.of(1000.0, 400.0))
    }

    @Test
    fun theSqlUsesTheSameHalfCentime() {
        assertEquals(Amount.HALF_CENTIME, PaymentState.HALF_CENTIME_SQL.toDouble(), 0.0)
        // Over what is left once later payments are spent (REP-02)
        assertEquals("(v.reste < 0.005)", PaymentState.sql(PaymentState.PAID, "v"))
        assertEquals("(o.reste >= 0.005 AND o.total - o.reste < 0.0049999999)", PaymentState.sql(PaymentState.UNPAID, "o"))
        assertEquals("(v.reste >= 0.005 AND v.total - v.reste >= 0.0049999999)", PaymentState.sql(PaymentState.PARTIAL, "v"))
        // Half a centime paid, read back from what is left
        assertEquals(PaymentState.PARTIAL, PaymentState.ofLeft(1000.0, 999.995))
    }

    /** From what is left: nothing left is paid, all of it unpaid, some of it partial (REP-02). */
    @Test
    fun theStateOfWhatIsLeft() {
        assertEquals(PaymentState.PAID, PaymentState.ofLeft(1000.0, 0.0))
        assertEquals(PaymentState.UNPAID, PaymentState.ofLeft(1000.0, 1000.0))
        assertEquals(PaymentState.PARTIAL, PaymentState.ofLeft(1000.0, 400.0))
        // Unknown: what was paid with it
        assertEquals(PaymentState.UNPAID, PaymentState.ofDocument(1000.0, 0.0, null))
        assertEquals(PaymentState.PAID, PaymentState.ofDocument(1000.0, 0.0, 0.0))
        assertEquals(600.0, PaymentState.paidSoFar(1000.0, 0.0, 400.0), 0.0)
    }

    @Test
    fun keysReadBothWays() {
        for (state in PaymentState.entries) assertEquals(state, PaymentState.fromKey(PaymentState.keyOf(state)))
        assertEquals(PaymentState.PAID, PaymentState.fromKey("PAYEE"))
        assertEquals(PaymentState.PARTIAL, PaymentState.fromKey("PARTIELLE"))
        assertEquals(PaymentState.UNPAID, PaymentState.fromKey("IMPAYEE"))
        assertNull(PaymentState.fromKey("TOUTES"))
        assertNull(PaymentState.fromKey(null))
    }
}
