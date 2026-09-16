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

object RetourFournisseurMotifs {
    val ALL = listOf(
        RetourMotifDefinition("Produit défectueux — repris par le fournisseur", StockEffect.DECREASE),
        RetourMotifDefinition("Produit défectueux — refusé (perte)",             StockEffect.DECREASE, perteType = DefaultPerteType.CASSE),
        RetourMotifDefinition("Produit périmé — repris par le fournisseur",      StockEffect.DECREASE),
        RetourMotifDefinition("Produit périmé — refusé (perte)",                 StockEffect.DECREASE, perteType = DefaultPerteType.PEREMPTION),
        RetourMotifDefinition("Erreur de commande",                              StockEffect.DECREASE),
        RetourMotifDefinition("Excédent de stock",                               StockEffect.DECREASE),
        RetourMotifDefinition("Autre",                                           StockEffect.DECREASE)
    )
    fun resolve(motif: String?): RetourMotifDefinition = ALL.find { it.id == motif } ?: ALL.last()
}
