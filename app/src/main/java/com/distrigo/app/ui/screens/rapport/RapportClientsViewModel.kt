package com.distrigo.app.ui.screens.rapport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class RapportClientsViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.AUJOURD_HUI)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()

    private var customStart: LocalDate? = null
    private var customEnd: LocalDate? = null

    private val _uiState = MutableStateFlow<RapportClientsUiState>(RapportClientsUiState.Loading)
    val uiState: StateFlow<RapportClientsUiState> = _uiState.asStateFlow()

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
            _uiState.value = RapportClientsUiState.Loading
            val range = _selectedPeriod.value.toDateRange(customStart = customStart, customEnd = customEnd)
            val kpis = repository.getClientsRapportKpis(range.startIso, range.endIso)
            val topClients = repository.getTopClients(range.startIso, range.endIso)
            val label = periodeLabel(range)

            _uiState.value = RapportClientsUiState.Content(
                RapportClientsData(
                    periodeLabel = label,
                    clientsActifs = kpis.clientsActifs,
                    nouveauxClients = kpis.nouveauxClients,
                    clientsPerdus = kpis.clientsPerdus,
                    clientsSansAchat = kpis.clientsSansAchat,
                    topClients = topClients
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


    private val _wilayaBreakdown = MutableStateFlow<List<com.distrigo.app.data.model.report.WilayaBreakdown>>(emptyList())
    val wilayaBreakdown: StateFlow<List<com.distrigo.app.data.model.report.WilayaBreakdown>> = _wilayaBreakdown.asStateFlow()

    init {
        load()
        loadWilayaBreakdown()
    }

    private fun loadWilayaBreakdown() {
        viewModelScope.launch {
            _wilayaBreakdown.value = repository.getClientsWilayaBreakdown()
        }
    }

    private val _clientInvoices = MutableStateFlow<List<com.distrigo.app.data.model.report.ClientInvoiceItem>>(emptyList())
    val clientInvoices: StateFlow<List<com.distrigo.app.data.model.report.ClientInvoiceItem>> = _clientInvoices.asStateFlow()

    fun loadClientInvoices(clientId: Int) {
        viewModelScope.launch {
            _clientInvoices.value = repository.getClientInvoices(clientId)
        }
    }

    private val _selectedVente = MutableStateFlow<com.distrigo.app.data.model.Vente?>(null)
    val selectedVente: StateFlow<com.distrigo.app.data.model.Vente?> = _selectedVente.asStateFlow()

    fun loadVenteDetail(venteId: Int) {   // ← أُعيدت تسميتها من loadVenteForReceipt
        viewModelScope.launch {
            _selectedVente.value = repository.getVente(venteId)
        }
    }

    fun clearSelectedVente() {
        _selectedVente.value = null
    }
}