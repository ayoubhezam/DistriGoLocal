package com.distrigo.app.ui.screens.rapport

sealed interface RapportVentesUiState {
    data object Loading : RapportVentesUiState
    data object Empty : RapportVentesUiState
    data class Content(val data: RapportVentesData) : RapportVentesUiState
}

data class RapportVentesData(
    val periodeLabel: String,        // مثال: "09 — 15 Juillet 2026"
    val genereLeLabel: String,       // مثال: "Généré le 15/07/2026"
    val chiffreAffaires: Int,
    val panierMoyen: Int,
    val nombreTickets: Int,
    val produitsVendus: Int,
    val clientsVisites: Int,
    val clientsAvecVente: Int,
    val clientsSansVente: Int
)

val RapportVentesData.totalClients: Int
    get() = clientsAvecVente + clientsSansVente

val RapportVentesData.avecVenteFraction: Float
    get() = if (totalClients == 0) 0f else clientsAvecVente.toFloat() / totalClients