package com.distrigo.app.ui.screens.rapport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class RapportVentesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db = db)

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.AUJOURD_HUI)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()

    private var customStart: LocalDate? = null
    private var customEnd: LocalDate? = null

    private val _uiState = MutableStateFlow<RapportVentesUiState>(RapportVentesUiState.Loading)
    val uiState: StateFlow<RapportVentesUiState> = _uiState.asStateFlow()

    init { load() }

    fun onPeriodSelected(period: ReportPeriod) {
        _selectedPeriod.value = period
        load()
    }

    fun onCustomRangeSelected(start: LocalDate, end: LocalDate) {
        customStart = start
        customEnd = end
        _selectedPeriod.value = ReportPeriod.PERSONNALISEE
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = RapportVentesUiState.Loading
            val range = _selectedPeriod.value.toDateRange(customStart = customStart, customEnd = customEnd)
            val kpis = repository.getVentesRapportKpis(range.startIso, range.endIso, range.previousStartIso, range.previousEndIso)
            val label = periodeLabel(range)

            val dailyMap = kpis.dailyBreakdown.associateBy { it.dateIso }
            val fmtFull = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val salesEvolution = range.allDatesInRange.map { date ->
                SalesPoint(
                    shortLabel = date.dayOfWeek.toFrenchShortLabel(),
                    fullDateLabel = date.format(fmtFull),
                    amount = dailyMap[date.toString()]?.amount ?: 0.0
                )
            }

            val totalSources = (kpis.depotTotal + kpis.tourneeTotal).coerceAtLeast(0.0)
            val sourcesVentes = listOf(
                SourceVenteItem(
                    label = "Dépôt Vente",
                    amount = kpis.depotTotal,
                    percent = if (totalSources == 0.0) 0 else ((kpis.depotTotal / totalSources) * 100).roundToInt()
                ),
                SourceVenteItem(
                    label = "Tournées",
                    amount = kpis.tourneeTotal,
                    percent = if (totalSources == 0.0) 0 else ((kpis.tourneeTotal / totalSources) * 100).roundToInt()
                )
            )
            val wilayaBreakdown = repository.getVentesWilayaBreakdown(range.startIso, range.endIso)
            val topSecteurs = repository.getTopSecteurs(range.startIso, range.endIso)

            if (kpis.ticketsCount == 0) {
                _uiState.value = RapportVentesUiState.Empty(label)
                return@launch
            }
            _uiState.value = RapportVentesUiState.Content(
                RapportVentesData(
                    periodeLabel = label,
                    chiffreAffairesTotal = kpis.totalVentes,
                    chiffreAffairesTotalTrend = calculateTrend(kpis.totalVentes, kpis.previousTotalVentes),
                    chiffreAffairesMoyenParJour = kpis.caMoyenParJour,
                    chiffreAffairesMoyenParJourTrend = calculateTrend(kpis.caMoyenParJour, kpis.previousCaMoyenParJour),
                    nombreTickets = kpis.ticketsCount,
                    nombreTicketsTrend = calculateTrend(kpis.ticketsCount.toDouble(), kpis.previousTicketsCount.toDouble()),
                    salesEvolution = salesEvolution,
                    panierMoyen = kpis.panierMoyen,
                    panierMoyenTrend = calculateTrend(kpis.panierMoyen, kpis.previousPanierMoyen),
                    sourcesVentes = sourcesVentes,
                    wilayaBreakdown = wilayaBreakdown,
                    topSecteurs = topSecteurs
                )
            )

        }
    }


    private fun periodeLabel(range: ReportDateRange): String {
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val startLabel = LocalDate.parse(range.startIso.substring(0, 10)).format(fmt)
        val endLabel = LocalDate.parse(range.endIso.substring(0, 10)).minusDays(1).format(fmt)
        return "$startLabel → $endLabel"
    }

    private fun calculateTrend(current: Double, previous: Double): TrendInfo {
        if (previous == 0.0) return TrendInfo(percentage = if (current > 0) 100 else 0, isPositive = current >= 0)
        val percent = ((current - previous) / previous * 100).roundToInt()
        return TrendInfo(percentage = kotlin.math.abs(percent), isPositive = percent >= 0)
    }
}