package com.distrigo.app.ui.screens.rapport

import androidx.compose.ui.graphics.Color

sealed interface RapportClientUiState {
    data object Loading : RapportClientUiState
    data object NoClientSelected : RapportClientUiState
    data class Content(val data: RapportClientData) : RapportClientUiState
}

/** بديل عن Pair<Color, Color> الخام في DsColors — أوضح عند تمريره كمعامل */
data class ClientCategoryTag(val textColor: Color, val bgColor: Color, val label: String)

data class TopProduct(val rank: Int, val name: String, val quantityLabel: String, val percentOfMax: Int)

data class MonthlyPurchase(val monthLabel: String, val amount: Int)

data class RapportClientData(
    val clientId: Long,
    val clientName: String,
    val clientInitials: String,
    val category: ClientCategoryTag,
    val address: String,
    val totalAchats: Int,
    val panierMoyen: Int,
    val nombreVisites: Int,
    val derniereVisiteLabel: String, // مثال: "Il y a 3 jours"
    val topProducts: List<TopProduct>,
    val monthlyPurchases: List<MonthlyPurchase>,
    val evolutionTrendLabel: String,
    val evolutionTrendIsUp: Boolean
)