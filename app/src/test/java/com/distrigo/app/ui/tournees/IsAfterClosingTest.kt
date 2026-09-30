package com.distrigo.app.ui.tournees

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ajouté après clôture": saved after the tournée's closing time, compared as instants. */
class IsAfterClosingTest {

    private val closed = "2026-09-27T18:00:00Z"

    @Test fun savedAfterClosing() = assertTrue(isAfterClosing("2026-09-28T08:15:30.123Z", closed))

    @Test fun savedBeforeClosing() = assertFalse(isAfterClosing("2026-09-27T17:59:59.999Z", closed))

    // As strings "…18:00:00.5Z" sorts before "…18:00:00Z"; as instants it is half a second later.
    @Test fun comparedAsInstantsNotText() = assertTrue(isAfterClosing("2026-09-27T18:00:00.5Z", closed))

    @Test fun openTourneeOrMissingDate() {
        assertFalse(isAfterClosing("2026-09-28T08:00:00Z", null))
        assertFalse(isAfterClosing(null, closed))
        assertFalse(isAfterClosing("pas une date", closed))
    }
}
