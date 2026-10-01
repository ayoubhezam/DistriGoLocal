package com.distrigo.app.ui.ventes

import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.model.Vente
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the delete confirmation says a sale's delete takes back — stock, solde, payment. */
class VenteDeleteEffectsTest {

    private val money = MoneyFormatter.plain(MoneyFormat.SPACES)

    private fun vente(source: String = "camion", total: Double = 1000.0, paid: Double? = 0.0, items: Int? = 3) = Vente(
        id = 7, client_id = 1, client_name = "Ahmed", tournee_id = 2, source = source, total = total,
        montant_paye = paid, status = "delivered", note = null, created_at = null, items_count = items
    )

    @Test fun unpaidCamionSale() = assertEquals(
        listOf(
            "Stock : les 3 produits vendus seront remis dans le stock camion.",
            "Solde : ce que doit le client « Ahmed » baissera de 1 000,00 DA (le reste à payer de cette vente)."
        ),
        venteDeleteEffects(vente(), money)
    )

    @Test fun partlyPaidDepotSaleOfOneProduct() = assertEquals(
        listOf(
            "Stock : le produit vendu sera remis dans le stock dépôt.",
            "Solde : ce que doit le client « Ahmed » baissera de 400,50 DA (le reste à payer de cette vente).",
            "Paiement : les 599,50 DA encaissés avec cette vente seront effacés avec elle."
        ),
        venteDeleteEffects(vente(source = "depot", paid = 599.5, items = 1), money)
    )

    @Test fun fullyPaidAndOverpaid() {
        assertEquals(
            "Solde : inchangé pour le client « Ahmed », la vente était entièrement payée.",
            venteDeleteEffects(vente(paid = 1000.0), money)[1]
        )
        assertEquals(
            "Solde : ce que doit le client « Ahmed » augmentera de 200,00 DA (l'avance payée avec cette vente).",
            venteDeleteEffects(vente(paid = 1200.0), money)[1]
        )
    }

    @Test fun unknownItemCount() = assertEquals(
        "Stock : les produits vendus seront remis dans le stock camion.",
        venteDeleteEffects(vente(items = null), money)[0]
    )

    /** The amounts follow the business's format. */
    @Test fun inTheChosenFormat() = assertEquals(
        "Paiement : les 1,599.50 DA encaissés avec cette vente seront effacés avec elle.",
        venteDeleteEffects(vente(total = 2000.0, paid = 1599.5), MoneyFormatter.plain(MoneyFormat.COMMAS))[2]
    )
}
