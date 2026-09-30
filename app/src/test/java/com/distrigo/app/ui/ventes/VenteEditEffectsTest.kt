package com.distrigo.app.ui.ventes

import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.VenteItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the edit confirmation says saving an edited sale changes. */
class VenteEditEffectsTest {

    private fun item(id: Int, productId: Int, name: String, qty: Double, unit: String = "carton") =
        VenteItem(id, productId, name, unit, qty, 100.0, qty * 100.0)

    private val original = Vente(
        id = 5, client_id = 1, client_name = "Ahmed", tournee_id = null, source = "depot", total = 800.0,
        montant_paye = 500.0, status = "delivered", note = null, created_at = null,
        items = listOf(item(1, 10, "Lait", 3.0), item(2, 20, "Huile", 5.0))
    )

    private fun line(productId: Int, name: String, qty: Double, unit: String = "carton") = EditedLine(productId, name, unit, qty)

    @Test fun nothingChangedAsksNothing() = assertEquals(
        emptyList<String>(),
        venteEditEffects(original, listOf(line(10, "Lait", 3.0), line(20, "Huile", 5.0)), 800.0, 500.0)
    )

    @Test fun moreLessAddedRemovedAndThePaymentLowered() = assertEquals(
        listOf(
            "« Lait » : 3 → 5, 2 cartons de plus sortiront du stock dépôt.",
            "« Huile » retiré : 5 cartons seront remis dans le stock dépôt.",
            "« Sucre » ajouté : 1 pièce sortira du stock dépôt.",
            "Total : 800 DA → 600 DA.",
            "Paiement : 500 DA → 200 DA — 300 DA encaissés seront effacés.",
            "Solde : ce que doit le client « Ahmed » augmentera de 100 DA."
        ),
        venteEditEffects(original, listOf(line(10, "Lait", 5.0), line(30, "Sucre", 1.0, "pièce")), 600.0, 200.0)
    )

    @Test fun onlyThePaymentRaised() = assertEquals(
        listOf(
            "Paiement : 500 DA → 800 DA encaissés.",
            "Solde : ce que doit le client « Ahmed » baissera de 300 DA."
        ),
        venteEditEffects(original, listOf(line(10, "Lait", 3.0), line(20, "Huile", 5.0)), 800.0, 800.0)
    )

    @Test fun fewerSoldGoesBack() = assertEquals(
        "« Huile » : 5 → 4.5, 0.5 carton sera remis dans le stock dépôt.",
        venteEditEffects(original, listOf(line(10, "Lait", 3.0), line(20, "Huile", 4.5)), 750.0, 500.0)[0]
    )
}
