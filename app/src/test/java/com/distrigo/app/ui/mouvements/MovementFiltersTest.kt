package com.distrigo.app.ui.mouvements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules the Mouvements filter sheet is built on. */
class MovementFiltersTest {

    @Test
    fun `nothing is filtered by default`() {
        val filters = MovementFilters()
        assertEquals(0, filters.activeCount)
        assertFalse(filters.periodIsBackwards)
    }

    @Test
    fun `a period that ends before it starts is refused`() {
        assertTrue(MovementFilters(dateFrom = "2026-09-20", dateTo = "2026-09-01").periodIsBackwards)
        assertFalse(MovementFilters(dateFrom = "2026-09-01", dateTo = "2026-09-20").periodIsBackwards)
        // One side alone is a half-open period, not a backwards one.
        assertFalse(MovementFilters(dateFrom = "2026-09-20").periodIsBackwards)
        assertFalse(MovementFilters(dateTo = "2026-09-01").periodIsBackwards)
        // The same day both ends is a single day, which is allowed.
        assertFalse(MovementFilters(dateFrom = "2026-09-20", dateTo = "2026-09-20").periodIsBackwards)
    }

    @Test
    fun `the badge counts the filters that are set, a period counting once`() {
        assertEquals(1, MovementFilters(dateFrom = "2026-09-01").activeCount)
        assertEquals(1, MovementFilters(dateFrom = "2026-09-01", dateTo = "2026-09-20").activeCount)
        assertEquals(
            4,
            MovementFilters(
                dateTo      = "2026-09-20",
                direction   = "entree",
                emplacement = "camion",
                types       = setOf(MovementType.ACHAT),
            ).activeCount
        )
        // A chosen party counts through its kind, which is always set with it.
        assertEquals(1, MovementFilters(party = MovementParty.CLIENT, partyId = 7).activeCount)
    }

    @Test
    fun `every kind of movement a ledger holds is offered`() {
        assertEquals(
            listOf("achat", "vente", "retour_client", "retour_fournisseur", "perte", "ajustement"),
            MovementType.entries.map { it.key }
        )
    }
}
