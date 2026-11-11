package com.distrigo.app.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** This phone's document counter marks (BAK-01): only ever raised, and a line it cannot read is skipped. */
class DocumentCounterMarksTest {

    @get:Rule val folder = TemporaryFolder()

    private fun marks() = DocumentCounterMarks(File(folder.root, "numbering/high-water"))

    @Test
    fun nothingRecordedYetIsEmpty() {
        assertEquals(emptyMap<String, Long>(), marks().read())
    }

    @Test
    fun aMarkIsRaisedNeverLowered() {
        val marks = marks()
        marks.raise(mapOf("numbering.ventes" to 12L, "numbering.purchase_orders" to 3L))
        // An older backup's counters: lower, so the marks stay
        marks.raise(mapOf("numbering.ventes" to 9L, "numbering.purchase_orders" to 5L, "numbering.retour_client" to 1L))
        assertEquals(
            mapOf("numbering.ventes" to 12L, "numbering.purchase_orders" to 5L, "numbering.retour_client" to 1L),
            marks.read()
        )
        // Read back by another instance, as after a restart
        assertEquals(12L, marks().read()["numbering.ventes"])
    }

    @Test
    fun aLineItCannotReadIsSkipped() {
        val file = File(folder.root, "numbering/high-water").apply { parentFile!!.mkdirs() }
        file.writeText("numbering.ventes=7\ngarbage\nnumbering.purchase_orders=abc\n=4\nnumbering.retour_client=-2\n")
        assertEquals(mapOf("numbering.ventes" to 7L), marks().read())
    }
}
