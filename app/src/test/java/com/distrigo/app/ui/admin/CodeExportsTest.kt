package com.distrigo.app.ui.admin

import com.distrigo.app.data.license.CodeOrder
import com.distrigo.app.data.license.NewCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/** A new batch as the owner shares it (docs/activation_codes.md, step C4). */
class CodeExportsTest {

    @Test
    fun `durations read as a merchant says them`() {
        assertEquals("1 mois", duration(30))
        assertEquals("3 mois", duration(90))
        assertEquals("6 mois", duration(182))
        assertEquals("1 an", duration(365))
        assertEquals("2 mois", duration(60))
        assertEquals("45 jours", duration(45))
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
