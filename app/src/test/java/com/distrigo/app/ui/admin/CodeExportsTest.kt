package com.distrigo.app.ui.admin

import com.distrigo.app.data.license.CodeOrder
import com.distrigo.app.data.license.NewCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A new batch as the owner shares it (docs/activation_codes.md, step C4). */
class CodeExportsTest {

    @Test
    fun `a typed duration gives whole days, from one day to three years`() {
        assertEquals(15, customDays("15", LengthUnit.DAYS))
        assertEquals(45, customDays(" 45 ", LengthUnit.DAYS))
        assertEquals(60, customDays("2", LengthUnit.MONTHS))
        assertEquals(730, customDays("2", LengthUnit.YEARS))
        assertEquals(1095, customDays("3", LengthUnit.YEARS))
        assertEquals(1100, customDays("1100", LengthUnit.DAYS))
        assertNull("past the database's limit", customDays("1101", LengthUnit.DAYS))
        assertNull(customDays("37", LengthUnit.MONTHS))
        assertNull(customDays("4", LengthUnit.YEARS))
        assertNull(customDays("0", LengthUnit.DAYS))
        assertNull(customDays("", LengthUnit.DAYS))
        assertNull(customDays("9999", LengthUnit.YEARS))
    }

    @Test
    fun `counts and refusals read as French says them`() {
        assertEquals("0 code", codes(0))
        assertEquals("1 code", codes(1))
        assertEquals("50 codes", codes(50))
        assertEquals("Ce lot ne peut pas être supprimé : un de ses codes ouvre une entreprise.", inUse(1))
        assertEquals("Ce lot ne peut pas être supprimé : 3 de ses codes ouvrent une entreprise.", inUse(3))
    }

    @Test
    fun `a custom batch's duration is shared as it was chosen`() {
        val batch = CreatedBatch(CodeOrder(count = 1, days = 730, batch = "Grossiste"), listOf(NewCode("7KQ4-M2XP-9HTD", null)))
        assertEquals(true, whatsAppText(batch).contains("DistriGo Solo · 2 ans par code"))
    }

    @Test
    fun `the WhatsApp message gives the batch, the codes in order, and how to use one`() {
        val batch = CreatedBatch(
            CodeOrder(count = 2, days = 182, batch = "Revendeur Sétif — oct. 2026", businessName = "  "),
            listOf(NewCode("7KQ4-M2XP-9HTD", LocalDate.of(2027, 10, 14)), NewCode("V1A9-Y8XW-JFC3", LocalDate.of(2027, 10, 14))),
        )
        assertEquals(
            """
            *DistriGo — codes d'activation*
            Lot : Revendeur Sétif — oct. 2026
            DistriGo Solo · 6 mois par code
            À activer avant le 14 octobre 2027

            1. 7KQ4-M2XP-9HTD
            2. V1A9-Y8XW-JFC3

            Pour activer : ouvrez DistriGo, puis « Activer avec un code ».
            """.trimIndent(),
            whatsAppText(batch),
        )
    }

    @Test
    fun `a code for one client names the company, and a code without limit says no date`() {
        val text = whatsAppText(
            CreatedBatch(CodeOrder(count = 1, days = 365, batch = "Benali", businessName = "Épicerie Benali"), listOf(NewCode("7KQ4-M2XP-9HTD", null))),
        )
        assertEquals(true, text.contains("Entreprise : Épicerie Benali"))
        assertFalse(text.contains("À activer avant"))
        assertEquals(true, text.contains("1. 7KQ4-M2XP-9HTD"))
    }
}
