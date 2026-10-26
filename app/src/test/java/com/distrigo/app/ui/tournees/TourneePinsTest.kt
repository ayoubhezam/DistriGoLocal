package com.distrigo.app.ui.tournees

import com.distrigo.app.data.local.dao.TourneePinRow
import com.distrigo.app.data.repository.VisitState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list's mini maps: each tournée's pins, their visit read as the map reads it, impossible positions left out. */
class TourneePinsTest {

    private fun row(tournee: Int, client: Int, status: String, bought: Boolean, lat: Double = 36.28, lng: Double = 7.95) =
        TourneePinRow(tournee, client, lat, lng, status, bought)

    @Test
    fun pinsArePerTourneeWithTheirVisit() {
        val pins = tourneePins(
            listOf(
                row(1, 1, "visite", bought = true),
                row(1, 2, "visite", bought = false),
                row(1, 3, "a_visiter", bought = false),
                row(1, 4, "a_visiter", bought = true),   // bought without being marked
                row(2, 5, "en_cours", bought = false),
            )
        )
        assertEquals(
            listOf(VisitState.WITH_SALE, VisitState.WITHOUT_SALE, VisitState.PENDING, VisitState.WITH_SALE),
            pins.getValue(1).map { it.state }
        )
        assertEquals(listOf(VisitState.PENDING), pins.getValue(2).map { it.state })
    }

    @Test
    fun aPositionAtZeroOrOutOfRangeIsNoPin() {
        val pins = tourneePins(listOf(row(1, 1, "visite", true, lat = 0.0, lng = 0.0), row(1, 2, "visite", true, lat = 95.0)))
        assertEquals(null, pins[1])
    }
}
