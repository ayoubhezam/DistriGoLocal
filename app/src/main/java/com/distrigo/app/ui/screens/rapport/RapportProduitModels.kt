package com.distrigo.app.ui.screens.rapport

sealed interface RapportProduitUiState {
    data object Loading : RapportProduitUiState
    data object NoProduitSelected : RapportProduitUiState
    data class Content(val data: RapportProduitData) : RapportProduitUiState
}

data class RankedClient(val rank: Int, val name: String, val quantityLabel: String, val isTopRank: Boolean)

data class RapportProduitData(
    val produitId: Long,
    val produitName: String,
    val categoryLabel: String, // مثال: "Huiles — Bidon 5L"
    val quantiteVendueLabel: String, // مثال: "1 240 unités"
    val chiffreAffairesGenere: Int,
    val nombreClientsAcheteurs: Int,
    val meilleursClients: List<RankedClient>,
    val monthlyPurchases: List<MonthlyPurchase>, // إعادة استخدام نفس النوع من RapportClientModels
    val evolutionTrendLabel: String, // مثال: "Meilleur mois : Juin"
    val evolutionTrendIsUp: Boolean
)