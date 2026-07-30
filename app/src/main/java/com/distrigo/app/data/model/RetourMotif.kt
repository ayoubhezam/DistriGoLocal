package com.distrigo.app.data.model

enum class StockEffect { INCREASE, DECREASE, NONE }

data class RetourMotifDefinition(
    val id: String,                    // exact string stored in RetourClientEntity.motif / RetourFournisseurEntity.motif — unchanged wire format
    val stockEffect: StockEffect,
    val perteTypeName: String? = null  // matched against PerteTypeEntity.name at runtime; null = no linked loss
)

object RetourClientMotifs {
    val ALL = listOf(
        RetourMotifDefinition("Produit défectueux", StockEffect.NONE, perteTypeName = "Casse"),
        RetourMotifDefinition("Produit périmé",      StockEffect.NONE, perteTypeName = "Péremption"),
        RetourMotifDefinition("Erreur de livraison", StockEffect.INCREASE),
        RetourMotifDefinition("Client insatisfait",  StockEffect.INCREASE),
        RetourMotifDefinition("Autre",               StockEffect.INCREASE)
    )
    fun resolve(motif: String?): RetourMotifDefinition = ALL.find { it.id == motif } ?: ALL.last()
}

object RetourFournisseurMotifs {
    val ALL = listOf(
        RetourMotifDefinition("Produit défectueux — repris par le fournisseur", StockEffect.DECREASE),
        RetourMotifDefinition("Produit défectueux — refusé (perte)",             StockEffect.DECREASE, perteTypeName = "Casse"),
        RetourMotifDefinition("Produit périmé — repris par le fournisseur",      StockEffect.DECREASE),
        RetourMotifDefinition("Produit périmé — refusé (perte)",                 StockEffect.DECREASE, perteTypeName = "Péremption"),
        RetourMotifDefinition("Erreur de commande",                              StockEffect.DECREASE),
        RetourMotifDefinition("Excédent de stock",                               StockEffect.DECREASE),
        RetourMotifDefinition("Autre",                                           StockEffect.DECREASE)
    )
    fun resolve(motif: String?): RetourMotifDefinition = ALL.find { it.id == motif } ?: ALL.last()
}
