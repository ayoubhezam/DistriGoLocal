package com.distrigo.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Dashboard's tournée and activity, off the phone (docs/design/dashboard_objectif_plan.md, Part 1 §6–8). */
class DashboardModelsTest {

    @Test
    fun `a client with a sale bought, one visited without did not, the rest wait`() {
        val day = TourneeDay.of(
            id = 4, label = "Adrar",
            clients = listOf(Triple(1, "Amel", null), Triple(2, "Badr", null), Triple(3, "Chafia", null), Triple(4, "Dris", null)),
            statuses = mapOf(1 to "visite", 2 to "visite", 3 to "a_visiter", 4 to "en_cours"),
            clientsWithSale = setOf(1, 3),
        )
        assertEquals(listOf(VisitState.WITH_SALE, VisitState.WITHOUT_SALE, VisitState.WITH_SALE, VisitState.PENDING), day.clients.map { it.state })
        assertEquals(2, day.withSale)
        assertEquals(1, day.withoutSale)
        assertEquals(1, day.pending)
        assertEquals(3, day.visited)
        assertEquals(4, day.total)
    }

    @Test
    fun `a sale is paid, partly paid or unpaid to the half centime`() {
        fun sale(total: Double, paid: Double) = RecentSale(1, null, null, null, "depot", total, paid, "2026-10-20T08:00:00Z")
        assertEquals(PaymentState.PAID, sale(1000.0, 1000.0).state)
        assertEquals(PaymentState.PAID, sale(1000.0, 999.996).state)
        assertEquals(PaymentState.PARTIAL, sale(1000.0, 400.0).state)
        assertEquals(PaymentState.UNPAID, sale(1000.0, 0.0).state)
        assertEquals(PaymentState.UNPAID, sale(1000.0, 0.004).state)
    }
}
