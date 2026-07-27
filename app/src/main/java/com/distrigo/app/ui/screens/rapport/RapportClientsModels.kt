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
    val clientsSansAchat: Int,
    val topClients: List<com.distrigo.app.data.model.report.ClientRankItem>
)

// توسيع ClientsDrillDown الموجودة أصلًا (إضافة حالة رابعة):
sealed interface ClientsDrillDown {
    data object None : ClientsDrillDown
    data class Communes(val wilaya: com.distrigo.app.data.model.report.WilayaBreakdown) : ClientsDrillDown
    data class Secteurs(
        val wilaya: com.distrigo.app.data.model.report.WilayaBreakdown,
        val commune: com.distrigo.app.data.model.report.CommuneBreakdown
    ) : ClientsDrillDown
    data object AllClients : ClientsDrillDown
    data class Invoices(val clientId: Int, val clientName: String) : ClientsDrillDown
    data class VenteDetail(val venteId: Int, val clientId: Int, val clientName: String) : ClientsDrillDown
}