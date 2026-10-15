package com.distrigo.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whose data a backup brings, before it is restored (docs/license_l4_checkin.md §7). */
class RestoreBusinessTest {

    @Test
    fun `only a backup bound to another business than the license's is worth a word`() {
        assertTrue(RestoreBusiness("b-2", "Épicerie Benali").isOther("b-1"))
        assertFalse(RestoreBusiness("b-1", "DISTRIGO").isOther("b-1"))
        assertFalse("a backup never bound takes the license's business", RestoreBusiness(null, "DISTRIGO").isOther("b-1"))
        assertFalse("without a license there is nothing to compare", RestoreBusiness("b-2", null).isOther(null))
    }

    @Test
    fun `the warning names the backup's business when it has a name`() {
        assertEquals(
            "Cette sauvegarde appartient à une autre entreprise (« Épicerie Benali ») que celle du compte connecté. " +
                "Après la restauration, DistriGo vous demandera de garder ces données pour votre entreprise, ou de vous déconnecter.",
            RestoreBusiness("b-2", "Épicerie Benali").line(),
        )
        assertTrue(RestoreBusiness("b-2", null).line().startsWith("Cette sauvegarde appartient à une autre entreprise que celle du compte connecté."))
    }
}
