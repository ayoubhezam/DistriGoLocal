package com.distrigo.app.data.model

enum class StockEffect { INCREASE, DECREASE, NONE }

data class RetourMotifDefinition(
    val id: String,                    // exact string stored in RetourClientEntity.motif / RetourFournisseurEntity.motif — unchanged wire format
    val stockEffect: StockEffect,
    /** The built-in perte type the linked loss is recorded under; null = no linked loss. */
    val perteType: DefaultPerteType? = null
)

object RetourClientMotifs {
    val ALL = listOf(
        RetourMotifDefinition("Produit défectueux", StockEffect.NONE, perteType = DefaultPerteType.CASSE),
        RetourMotifDefinition("Produit périmé",      StockEffect.NONE, perteType = DefaultPerteType.PEREMPTION),
        RetourMotifDefinition("Erreur de livraison", StockEffect.INCREASE),
        RetourMotifDefinition("Client insatisfait",  StockEffect.INCREASE),
        RetourMotifDefinition("Autre",               StockEffect.INCREASE)
    )
    fun resolve(motif: String?): RetourMotifDefinition = ALL.find { it.id == motif } ?: ALL.last()
}

/**
 * A supplier return is only for goods the supplier takes back (FIN-03; owner, 2026-11-10): what they refuse is
 * recorded in Pertes, as Casse or Péremption, and lowers nothing owed to them.
 */
object RetourFournisseurMotifs {
    /** The motifs a new return offers. */
    val ALL = listOf(
        RetourMotifDefinition("Produit défectueux — repris par le fournisseur", StockEffect.DECREASE),
        RetourMotifDefinition("Produit périmé — repris par le fournisseur",      StockEffect.DECREASE),
        RetourMotifDefinition("Erreur de commande",                              StockEffect.DECREASE),
        RetourMotifDefinition("Excédent de stock",                               StockEffect.DECREASE),
        RetourMotifDefinition("Autre",                                           StockEffect.DECREASE)
    )

    /**
     * Goods the supplier refused, as returns made before FIN-03 recorded them — sent out of the dépôt with a linked
     * perte. No longer offered, and refused by the repository; kept so those returns still read as they were made.
     */
    val RETIRED = listOf(
        RetourMotifDefinition("Produit défectueux — refusé (perte)", StockEffect.DECREASE, perteType = DefaultPerteType.CASSE),
        RetourMotifDefinition("Produit périmé — refusé (perte)",     StockEffect.DECREASE, perteType = DefaultPerteType.PEREMPTION)
    )

    fun isRetired(motif: String?): Boolean = RETIRED.any { it.id == motif }

    fun resolve(motif: String?): RetourMotifDefinition = (ALL + RETIRED).find { it.id == motif } ?: ALL.last()
}
