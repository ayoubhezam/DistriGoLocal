package com.distrigo.app.ui.designsystem

import com.distrigo.app.data.license.LicensePolicy
import com.distrigo.app.data.license.LicensePolicy.Action
import com.distrigo.app.data.license.LicensePolicy.Banner
import com.distrigo.app.data.license.LicensePolicy.Block
import com.distrigo.app.data.license.LicensePolicy.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screens' side of the license (docs/license_l5_enforcement.md §2, §8.1, decision 5). */
class LicenseUiTest {

    private val block = Block("Votre abonnement a expiré le 31/12/2026.", Action.SUBSCRIPTION, "Réactiver mon abonnement")
    private val readOnly = LicensePolicy(false, Banner("Abonnement expiré — consultation seule.", Tone.READ_ONLY, Action.SUBSCRIPTION), block)

    @Test
    fun `a write runs while recording is allowed`() {
        val ui = LicenseUi()
        var ran = false
        ui.runOrExplain { ran = true }
        assertTrue(ran)
        assertNull(ui.shown)
        assertFalse(ui.refuses())
    }

    @Test
    fun `read-only, the write does not run and the sheet says why`() {
        val ui = LicenseUi().apply { policy = readOnly }
        var ran = false
        ui.guarded { ran = true }()
        assertFalse(ran)
        assertEquals(block, ui.shown)
        ui.closeSheet()
        assertNull(ui.shown)
        assertTrue(ui.refuses())
        assertEquals(block, ui.shown)
    }

    @Test
    fun `before the license is read, nothing is refused by the screens`() {
        val ui = LicenseUi().apply { policy = LicensePolicy.UNCHECKED }
        var ran = false
        ui.runOrExplain { ran = true }
        assertTrue(ran)
        ui.explain()
        assertNull(ui.shown)
    }

    @Test
    fun `a write the gate refused opens the sheet only when the license refuses`() {
        val ui = LicenseUi()
        ui.explain()
        assertNull(ui.shown)
        ui.policy = readOnly
        ui.explain()
        assertEquals(block, ui.shown)
    }

    @Test
    fun `a warning closes for the day, a read-only banner stays`() {
        var day = "2026-12-28"
        val kept = mutableListOf<String>()
        val ui = LicenseUi(onDismissed = { kept += it }, today = { day })
        val warning = Banner("Votre abonnement expire le 31/12/2026.", Tone.WARNING, Action.SUBSCRIPTION, dismissible = true)
        ui.policy = LicensePolicy(true, warning)
        assertEquals(warning, ui.banner)
        ui.dismiss(warning)
        assertNull(ui.banner)
        assertEquals(listOf("2026-12-28|${warning.text}"), kept)

        day = "2026-12-29"
        assertEquals("back the next day", warning, ui.banner)

        ui.policy = readOnly
        assertEquals(readOnly.banner, ui.banner)
    }
}
