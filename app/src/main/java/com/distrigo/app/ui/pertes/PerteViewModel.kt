package com.distrigo.app.ui.pertes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockPolicy
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.data.local.paging.ProductSort
import com.distrigo.app.ui.common.PagedProductList
import com.distrigo.app.ui.common.debouncedSearch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerteViewModel @Inject constructor(
    private val repository: PerteRepository,
    private val productRepository: ProductRepository,
    businessSettings: BusinessSettingsRepository
) : ViewModel() {

    // ── قائمة الأنواع ──
    private val _perteTypes = MutableStateFlow<List<PerteType>>(emptyList())
    val perteTypes: StateFlow<List<PerteType>> = _perteTypes

    // ── خسائر النوع المفتوح حالياً ──
    private val _pertes = MutableStateFlow<List<Perte>>(emptyList())
    val pertes: StateFlow<List<Perte>> = _pertes

    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            repository.seedDefaultPerteTypesIfNeeded()
            loadPerteTypes()
        }
    }

    // A month chosen explicitly stays chosen; otherwise "ce mois" follows the calendar, so a screen
    // left open across a month boundary stops showing the previous month's totals. The screens key
    // their loads on selectedMonth, so either kind of change reloads what is on screen.
    private var monthPinned = false

    fun setSelectedMonth(month: String) {
        monthPinned = true
        _selectedMonth.value = month
    }

    private fun activeMonth(): String {
        val today = currentMonth()
        if (!monthPinned && _selectedMonth.value != today) _selectedMonth.value = today
        return _selectedMonth.value
    }

    // The screen opening and init both ask for the types, and every save asks again. A new request
    // cancels the one still running, so the two opening loads collapse into one and a slow earlier
    // load can never overwrite a newer result.
    private var perteTypesLoad: Job? = null
    private var perteTypesGeneration = 0

    fun loadPerteTypes() {
        perteTypesLoad?.cancel()
        val generation = ++perteTypesGeneration
        perteTypesLoad = viewModelScope.launch {
            _isLoading.value = true
            try {
                _perteTypes.value = repository.getPerteTypesWithStats(activeMonth())
                _error.value = null
            } catch (e: CancellationException) {
                throw e   // superseded by a newer load: not an error to show
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                // A cancelled load finishes after its replacement has started; only the latest
                // load may turn the spinner off.
                if (generation == perteTypesGeneration) _isLoading.value = false
            }
        }
    }

    fun loadPertes(typeId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _pertes.value = repository.getPertes(typeId, activeMonth())
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addPerteType(
        name      : String,
        icon      : String,
        colorHex  : String,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addPerteType(name, icon, colorHex)
                loadPerteTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deletePerteType(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deletePerteType(id)
                loadPerteTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    /** A perte of [typeId] changed or went away from its details: its list and the month's totals reload. */
    fun refreshAfterChange(typeId: Int) {
        loadPertes(typeId)
        loadPerteTypes()
    }

    fun deletePerte(
        id        : Int,
        typeId    : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deletePerte(id)
                loadPertes(typeId)
                loadPerteTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    companion object {
        fun currentMonth(): String = java.time.LocalDate.now().toString().take(7)
    }
}