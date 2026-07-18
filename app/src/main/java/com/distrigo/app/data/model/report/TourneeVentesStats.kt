package com.distrigo.app.data.model.report

/** نتيجة خام من طبقة البيانات — لا تنسيق عرض هنا، فقط أرقام. */
data class TourneeVentesStats(
    val totalVentes: Double,
    val ticketsCount: Int,
    val panierMoyen: Double,
    val previousTotalVentes: Double,
    val previousTicketsCount: Int,
    val previousPanierMoyen: Double,
    val dailyBreakdown: List<DailySalesAmount>
)

data class DailySalesAmount(val dateIso: String, val amount: Double) // dateIso مثل "2026-07-14"