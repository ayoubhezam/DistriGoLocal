package com.distrigo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNumberLabelTest {

    @Test
    fun anOlderDocumentKeepsItsHashNumber() {
        assertEquals("#26", numberLabel("26", 26))
        assertEquals("26", receiptNumber("26", 26))
    }

    /** After a sync the local id differs; the stored number is what shows. */
    @Test
    fun theStoredNumberWinsOverTheLocalId() {
        assertEquals("#26", numberLabel("26", 311))
    }

    @Test
    fun aNewDocumentShowsItsFullNumber() {
        assertEquals("V-6DED-000027", numberLabel("V-6DED-000027", 27))
        assertEquals("V-6DED-000027", receiptNumber("V-6DED-000027", 27))
    }

    @Test
    fun withoutANumberTheIdStandsIn() {
        assertEquals("#27", numberLabel(null, 27))
        assertEquals("#27", numberLabel("", 27))
        assertEquals("27", receiptNumber(null, 27))
    }
}
