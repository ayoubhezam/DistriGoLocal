package com.distrigo.app.data.model.report

interface GeoAmountItem {
    val name: String
    val amount: Double
    val percent: Int
}

data class SecteurBreakdown(
    override val name: String,
    override val amount: Double,
    override val percent: Int
) : GeoAmountItem

data class CommuneBreakdown(
    override val name: String,
    override val amount: Double,
    override val percent: Int,
    val secteurs: List<SecteurBreakdown>
) : GeoAmountItem

data class WilayaBreakdown(
    override val name: String,
    override val amount: Double,
    override val percent: Int,
    val communes: List<CommuneBreakdown>
) : GeoAmountItem

data class SecteurRankItem(
    val secteurId: Int,
    val name: String,
    val subtitle: String,   // "Commune, Wilaya" — بدل أعمدة منفصلة
    val amount: Double,
    val percent: Int,
    val rank: Int,
    val isWeak: Boolean
)