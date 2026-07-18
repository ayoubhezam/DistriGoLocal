package com.distrigo.app.ui.screens.rapport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.local.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TableauDeBordViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db = db)

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.SEMAINE)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()

    private val _uiState = MutableStateFlow<TableauDeBordUiState>(TableauDeBordUiState.Loading)
    val uiState: StateFlow<TableauDeBordUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onPeriodSelected(period: ReportPeriod) {
        _selectedPeriod.value = period
        load()
    }

    fun refresh() {
        load()
    }


    private fun load() {
        viewModelScope.launch {
            _uiState.value = TableauDeBordUiState.Loading

            val range = _selectedPeriod.value.toDateRange()
            val ventesStats = repository.getTourneeVentesStats(
                startIso = range.startIso,
                endIso = range.endIso,
                previousStartIso = range.previousStartIso,
                previousEndIso = range.previousEndIso
            )

            if (ventesStats.ticketsCount == 0) {
                _uiState.value = TableauDeBordUiState.Empty
                return@launch
            }

            _uiState.value = TableauDeBordUiState.Content(mapToUiData(range, ventesStats))
        }
    }

    private fun mapToUiData(
        range: ReportDateRange,
        stats: com.distrigo.app.data.model.report.TourneeVentesStats
    ): TableauDeBordData {
        val dailyMap = stats.dailyBreakdown.associateBy { it.dateIso }
        val salesTrend = range.allDatesInRange.map { date ->
            SalesBarData(
                dayLabel = date.toShortDayLabel(),
                amount = (dailyMap[date.toString()]?.amount ?: 0.0).roundToInt()
            )
        }

        return TableauDeBordData(
            // TODO: يعتمد على "Objectif" غير موجود بعد في قاعدة البيانات — سنبنيه في خطوة لاحقة
            insightMessage = "",
            objectifMontant = 0,
            realiseMontant = stats.totalVentes.roundToInt(),
            tauxAtteinte = 0,
            jourActuel = 0,
            jourTotal = 0,
            enRetard = false,
            retardJours = 0,
            gapMontant = 0,
            gapClientsEstimes = 0,

            ventesRealisees = stats.totalVentes.roundToInt(),
            ventesTrend = calculateTrend(stats.totalVentes, stats.previousTotalVentes),
            nombreTickets = stats.ticketsCount,
            ticketsTrend = calculateTrend(stats.ticketsCount.toDouble(), stats.previousTicketsCount.toDouble()),
            caMoyenParTicket = stats.panierMoyen.roundToInt(),
            caMoyenTrend = calculateTrend(stats.panierMoyen, stats.previousPanierMoyen),
            salesTrend = salesTrend,

            // TODO: قسم Visites — الخطوة القادمة (Clients)
            clientsVisites = 0,
            visitesTrend = TrendInfo(0, true),
            clientsAvecVente = 0,
            clientsSansVente = 0,
            atRiskClients = emptyList(),

            // TODO: قسم Performance — خطوة Produits
            produitsVendus = 0,
            tourneesRealisees = 0,
            tempsMoyenParTourneeMinutes = 0,
            meilleurClientNom = "",
            meilleurClientMontant = 0
        )
    }

    private fun calculateTrend(current: Double, previous: Double): TrendInfo {
        if (previous == 0.0) {
            return TrendInfo(percentage = if (current > 0) 100 else 0, isPositive = current >= 0)
        }
        val percent = ((current - previous) / previous * 100).roundToInt()
        return TrendInfo(percentage = kotlin.math.abs(percent), isPositive = percent >= 0)
    }



}