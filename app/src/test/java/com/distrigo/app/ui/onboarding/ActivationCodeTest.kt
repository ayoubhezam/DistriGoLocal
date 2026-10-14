package com.distrigo.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone reads codes as the server does (supabase/migrations/20261014120000_activation_codes.sql). */
class ActivationCodeTest {

    // Made by the live server's private.new_code() on 2026-10-14; none of them was ever stored as a code.
    private val serverCodes = listOf("V8A2NG3ZPBEQ", "61YJRGPRXDMD", "N24N6Y77QZ09", "RWHG9Z0XGDH1", "FDKRAV49QJKE", "HQJ42NQZ85MR")
    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @Test
    fun `codes the server made pass the check character here too`() {
        serverCodes.forEach { assertTrue(it, isWellFormedCode(it)) }
    }

    @Test
    fun `any one wrong character, anywhere, is a typo the phone catches`() {
        for (code in serverCodes) {
            for (i in code.indices) {
                for (c in alphabet) {
                    if (c == code[i]) continue
                    val typo = code.substring(0, i) + c + code.substring(i + 1)
                    assertFalse("$code → $typo", isWellFormedCode(typo))
                }
            }
        }
    }

    @Test
    fun `a code is read as typed or copied from paper`() {
        assertEquals("7KQ4M2XP9HTD", normalizeCode("7kq4 m2xp-9htd"))
        assertEquals("011", normalizeCode("oIl"))
        assertEquals("", normalizeCode("u ü #"))
        assertEquals(CODE_LENGTH, normalizeCode("V8A2-NG3Z-PBEQ-EXTRA").length)
        assertEquals("V8A2-NG3Z-PBEQ", formatCode("V8A2NG3ZPBEQ"))
        assertFalse(isWellFormedCode("V8A2NG3ZPBE"))
    }

    @Test
    fun `every label of the sign-up's lists has the code the server stores`() {
        ACTIVITIES.forEach { assertNotNull(it, activityCode(it)) }
        SALES_REPS.forEach { assertNotNull(it, salesRepsCode(it)) }
        assertEquals("frais", activityCode("Produits laitiers et frais"))
        assertEquals("10+", salesRepsCode("Plus de 10"))
        assertNull(activityCode("Épicerie"))
    }
}
