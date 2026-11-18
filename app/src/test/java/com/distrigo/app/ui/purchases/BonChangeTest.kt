package com.distrigo.app.ui.purchases

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which change a bon offers: the Achats list's long-press, the detail's menu and the edit form all ask [bonChange]. */
class BonChangeTest {

    @Test
    fun `a received bon is reopened, never edited in place`() {
        assertEquals(BonChange.REOPEN, bonChange("received"))
    }

    @Test
    fun `a pending bon, or one whose status is not known yet, is edited`() {
        assertEquals(BonChange.EDIT, bonChange("pending"))
        assertEquals(BonChange.EDIT, bonChange(null))
    }
}
