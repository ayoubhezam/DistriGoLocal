package com.distrigo.app.data.model.report

data class VentesRapportKpis(
    val totalVentes: Double,
    val ticketsCount: Int,
    val caMoyenParJour: Double,
    val panierMoyen: Double,
    val previousTotalVentes: Double,
    val previousTicketsCount: Int,
    val previousCaMoyenParJour: Double,
    val previousPanierMoyen: Double,
    val dailyBreakdown: List<DailySalesAmount>,
    val depotTotal: Double,
    val tourneeTotal: Double,
)