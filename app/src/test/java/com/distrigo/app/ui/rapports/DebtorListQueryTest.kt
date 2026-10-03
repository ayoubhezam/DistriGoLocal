package com.distrigo.app.ui.rapports

import com.distrigo.app.data.repository.DebtorLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Searching and ordering the full debtor list. */
class DebtorListQueryTest {

    private fun debtor(id: Int, name: String, balance: Double) =
        DebtorLine(id, name, balance, listOf(balance, 0.0, 0.0, 0.0), lastPayment = null)

    private val debtors = listOf(
        debtor(1, "Superette Karim", 500.0),
        debtor(2, "Épicerie Sofiane", 9000.0),
        debtor(3, "alimentation Samir", 1200.0),
        debtor(4, "Zitouni Gros", 50.0),
    )

    private fun names(query: String, sort: DebtorSort) = debtorsMatching(debtors, query, sort).map { it.name }

    @Test
    fun `by debt, biggest first by default and smallest first on request`() {
        assertEquals(listOf("Épicerie Sofiane", "alimentation Samir", "Superette Karim", "Zitouni Gros"), names("", DebtorSort.DETTE_DESC))
        assertEquals(listOf("Zitouni Gros", "Superette Karim", "alimentation Samir", "Épicerie Sofiane"), names("", DebtorSort.DETTE_ASC))
    }

    @Test
    fun `by name, in French order, whatever the case and the accents`() {
        assertEquals(listOf("alimentation Samir", "Épicerie Sofiane", "Superette Karim", "Zitouni Gros"), names("", DebtorSort.NOM_AZ))
        assertEquals(listOf("Zitouni Gros", "Superette Karim", "Épicerie Sofiane", "alimentation Samir"), names("", DebtorSort.NOM_ZA))
    }

    @Test
    fun `the search keeps names holding every word, in any order and any case`() {
        assertEquals(listOf("Superette Karim"), names("karim", DebtorSort.DETTE_DESC))
        assertEquals(listOf("alimentation Samir"), names("samir ALIM", DebtorSort.DETTE_DESC))
        assertEquals(listOf("Épicerie Sofiane"), names("épicerie", DebtorSort.DETTE_DESC))
        assertEquals(emptyList<String>(), names("introuvable", DebtorSort.DETTE_DESC))
    }
}
