package com.distrigo.app.ui.screens.rapport

sealed interface RapportVentesUiState {
    data object Loading : RapportVentesUiState
    data class Empty(val periodeLabel: String) : RapportVentesUiState
    data class Content(val data: RapportVentesData) : RapportVentesUiState
}

data class RapportVentesData(
    val periodeLabel: String,
    val chiffreAffairesTotal: Double,
    val chiffreAffairesTotalTrend: TrendInfo,
    val chiffreAffairesMoyenParJour: Double,
    val chiffreAffairesMoyenParJourTrend: TrendInfo,
    val nombreTickets: Int,
    val nombreTicketsTrend: TrendInfo,
    val salesEvolution: List<SalesPoint>,
    val panierMoyen: Double,
    val panierMoyenTrend: TrendInfo,
    val sourcesVentes: List<SourceVenteItem>,
    val wilayaBreakdown: List<com.distrigo.app.data.model.report.WilayaBreakdown>,
    val topSecteurs: List<com.distrigo.app.data.model.report.SecteurRankItem>

)

data class SalesPoint(
    val shortLabel: String,     // "15/06" — محور X
    val fullDateLabel: String,  // "18/06/2025" — Tooltip
    val amount: Double
)

data class SourceVenteItem(
    val label: String,   // "Dépôt Vente" / "Tournées"
    val amount: Double,
    val percent: Int
)

sealed interface VentesDrillDown {
    data object None : VentesDrillDown
    data class Communes(val wilaya: com.distrigo.app.data.model.report.WilayaBreakdown) : VentesDrillDown
    data class Secteurs(
        val wilaya: com.distrigo.app.data.model.report.WilayaBreakdown,
        val commune: com.distrigo.app.data.model.report.CommuneBreakdown
    ) : VentesDrillDown
}