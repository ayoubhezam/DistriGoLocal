package com.distrigo.app.ui.common

import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.purchases.formatOrderDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The Dépôt Vente and Achats lists put a document made after local midnight under its local day. */
class ListFiltersLocalDayTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    private fun vente(id: Int, createdAt: String) = Vente(
        id = id, client_id = 1, client_name = "Épicerie El Amel", tournee_id = null, source = "depot",
        total = 100.0, montant_paye = 0.0, status = "pending", note = null, created_at = createdAt
    )

    @Test
    fun `a sale at 00h30 is grouped and filtered under its own day`() {
        val ventes = listOf(
            vente(1, "2026-09-16T22:30:00Z"),   // 23:30 on the 16th
            vente(2, "2026-09-16T23:30:00Z"),   // 00:30 on the 17th
        )
        assertEquals(
            mapOf("2026-09-16" to listOf(ventes[0]), "2026-09-17" to listOf(ventes[1])),
            groupVentesByDay(ventes, algiers)
        )
        val onlyThe17th = VenteListFilters(dateFrom = "2026-09-17", dateTo = "2026-09-17")
        assertEquals(listOf(ventes[1]), filterVentes(ventes, "", onlyThe17th, algiers))
    }

    @Test
    fun `a bon is dated by its creation instant, or its calendar date without one`() {
        val atNight = PurchaseOrder(id = 1, date = "2026-09-16", total = 0.0, status = "pending", note = null,
            supplier_id = 1, supplier_name = "Laiterie Soummam", created_at = "2026-09-16T23:30:00Z")
        val undated = atNight.copy(id = 2, created_at = null)
        assertEquals(
            mapOf("2026-09-17" to listOf(atNight), "2026-09-16" to listOf(undated)),
            groupOrdersByDay(listOf(atNight, undated), algiers)
        )
    }

    @Test
    fun `a sale made after local midnight today reads Aujourd'hui`() {
        val today = LocalDate.now(algiers)
        val justAfterMidnight = today.atStartOfDay(algiers).plusMinutes(30).toInstant().toString()
        assertEquals("Aujourd'hui", formatOrderDate(justAfterMidnight, algiers))
        assertEquals("Hier", formatOrderDate(today.minusDays(1).toString(), algiers))
    }
}
