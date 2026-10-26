package com.distrigo.app.data.local.dao

import org.junit.Assert.assertEquals
import org.junit.Test

/** The tournée page's « client(s) visité(s) », counted as the card counts it in SQL (TourneeVisitsTest, device). */
class TourneeVisitsTest {

    @Test
    fun aClientVisitedOrWhoBoughtCountsOnce() {
        // 1 visited and bought, 2 visited only, 3 bought only, 5 bought twice off the list
        assertEquals(4, TourneeVisits.count(visitedOnList = listOf(1, 2), buyers = listOf(1, 3, 5, 5)))
    }

    @Test
    fun nothingVisitedNorSoldIsNone() {
        assertEquals(0, TourneeVisits.count(visitedOnList = emptyList(), buyers = emptyList()))
    }
}
