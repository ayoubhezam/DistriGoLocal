package com.distrigo.app.ui.screens.rapport

sealed interface RapportClientsUiState {
    data object Loading : RapportClientsUiState
    data class Content(val data: RapportClientsData) : RapportClientsUiState
}

data class RapportClientsData(
    val periodeLabel: String,
    val clientsActifs: Int,
    val nouveauxClients: Int,
    val clientsPerdus: Int,
    val clientsSansAchat: Int
)