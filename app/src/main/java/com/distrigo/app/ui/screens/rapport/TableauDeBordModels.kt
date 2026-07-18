package com.distrigo.app.ui.screens.rapport

import java.util.Locale

/**
 * الحالة الكاملة لتبويب "Tableau de bord".
 * القيم هنا خام (Int/Boolean) وليست نصوصًا جاهزة للعرض؛ التنسيق (مثل "18 750 DA")
 * يتم داخل الـ Composables عبر دوال تنسيق بسيطة، أو لاحقًا عبر UseCase/Mapper عند ربط البيانات الحقيقية.
 */
sealed interface TableauDeBordUiState {
    data object Loading : TableauDeBordUiState
    data object Empty : TableauDeBordUiState
    data class Content(val data: TableauDeBordData) : TableauDeBordUiState
}

data class TableauDeBordData(
    val insightMessage: String,

    // Hero KPI
    val objectifMontant: Int,
    val realiseMontant: Int,
    val tauxAtteinte: Int, // 0..100+
    val jourActuel: Int,
    val jourTotal: Int,
    val enRetard: Boolean,
    val retardJours: Int,
    val gapMontant: Int,
    val gapClientsEstimes: Int,

    // Ventes
    val ventesRealisees: Int,
    val ventesTrend: TrendInfo,
    val nombreTickets: Int,
    val ticketsTrend: TrendInfo,
    val caMoyenParTicket: Int,
    val caMoyenTrend: TrendInfo,
    val salesTrend: List<SalesBarData>,

    // Visites
    val clientsVisites: Int,
    val visitesTrend: TrendInfo,
    val clientsAvecVente: Int,
    val clientsSansVente: Int,
    val atRiskClients: List<AtRiskClient>,

    // Performance
    val produitsVendus: Int,
    val tourneesRealisees: Int,
    val tempsMoyenParTourneeMinutes: Int,
    val meilleurClientNom: String,
    val meilleurClientMontant: Int
)

data class SalesBarData(val dayLabel: String, val amount: Int)

data class AtRiskClient(val clientId: Long, val name: String)

/** نسبة التقدم في الفترة (Jour X/Y) كـ 0f..1f */
val TableauDeBordData.paceFraction: Float
    get() = if (jourTotal == 0) 0f else (jourActuel.toFloat() / jourTotal).coerceIn(0f, 1f)

/** نسبة تحقيق الهدف كـ 0f..1f، تُستخدم لرسم الحلقة (تُحدّ عند 100% بصريًا) */
val TableauDeBordData.progressFraction: Float
    get() = (tauxAtteinte / 100f).coerceIn(0f, 1f)

val TableauDeBordData.totalClientsForDonut: Int
    get() = clientsAvecVente + clientsSansVente

// ─────────────────────────────
// دوال تنسيق العرض (Formatting utils)
// ─────────────────────────────

/** تنسيق مبلغ بفواصل آلاف (مثال: 18750 -> "18 750") مع لاحقة العملة */
fun formatCurrency(amount: Int): String {
    val formatted = String.format(Locale.FRANCE, "%,d", amount).replace(",", " ")
    return "$formatted DA"
}

fun formatMinutesAsDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

fun percentOf(part: Int, total: Int): Int =
    if (total == 0) 0 else ((part.toFloat() / total) * 100).toInt()