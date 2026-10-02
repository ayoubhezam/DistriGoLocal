package com.distrigo.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a restore stops to say what it does to "Autoriser le stock négatif", and what it says. */
class RestoreStockRuleTest {

    private fun rule(backup: Boolean, current: Boolean, negative: Int = 0, reset: Boolean = false) =
        RestoreStockRule(backupAllowsNegative = backup, currentAllowsNegative = current, negativeProducts = negative, resetByUpgrade = reset)

    @Test
    fun `the same rule with nothing below zero restores without a word`() {
        assertFalse(rule(backup = true, current = true).worthTelling)
        assertFalse(rule(backup = false, current = false).worthTelling)
        // Negative products under an allowed rule are what that rule allows.
        assertFalse(rule(backup = true, current = true, negative = 12).worthTelling)
    }

    @Test
    fun `a different rule is always told`() {
        assertTrue(rule(backup = true, current = false).worthTelling)
        assertTrue(rule(backup = false, current = true).worthTelling)
    }

    @Test
    fun `products below zero under a strict rule are told, even when the rule does not change`() {
        val r = rule(backup = false, current = false, negative = 3)
        assertTrue(r.worthTelling)
        assertTrue(r.lines().any {
            it.startsWith("3 produit(s) sont déjà en stock négatif au dépôt dans cette sauvegarde.") &&
                it.contains("vendus, chargés ni sortis du dépôt avant d'être réapprovisionnés")
        })
    }

    @Test
    fun `the backup keeps its rule, and the dialog says which one wins`() {
        val lines = rule(backup = false, current = true, negative = 0).lines()
        assertEquals(
            "Stock négatif : interdit dans la sauvegarde, autorisé sur ce téléphone. " +
                "La sauvegarde garde sa règle : après la restauration, le stock négatif sera interdit.",
            lines.first()
        )
        assertFalse("no products line without negative products", lines.any { "produit(s)" in it })
    }

    @Test
    fun `a backup older than the setting says it took the default`() {
        val r = rule(backup = true, current = false, reset = true)
        assertTrue(r.worthTelling)
        assertTrue(r.lines().any { "antérieure à ce réglage" in it && "« autorisé »" in it })
        // Negative products under the default, allowed rule: nothing to warn about.
        assertFalse(rule(backup = true, current = false, negative = 5, reset = true).lines().any { "produit(s)" in it })
    }
}
