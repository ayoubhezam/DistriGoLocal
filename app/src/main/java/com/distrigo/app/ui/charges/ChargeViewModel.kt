package com.distrigo.app.ui.charges

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Charge
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.data.repository.ChargeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChargeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ChargeRepository(db.chargeDao())

    // ── قائمة الأنواع (Types) ──
    private val _chargeTypes = MutableStateFlow<List<ChargeType>>(emptyList())
    val chargeTypes: StateFlow<List<ChargeType>> = _chargeTypes

    // ── الأنواع الفرعية (SubTypes) للنوع المفتوح حالياً ──
    private val _subTypes = MutableStateFlow<List<ChargeSubType>>(emptyList())
    val subTypes: StateFlow<List<ChargeSubType>> = _subTypes

    // ── المصاريف للنوع الفرعي المفتوح حالياً ──
    private val _charges = MutableStateFlow<List<Charge>>(emptyList())
    val charges: StateFlow<List<Charge>> = _charges

    // ── الشهر المختار (لتصفية القوائم أعلاه) ──
    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            repository.seedDefaultChargeTypesIfNeeded()
            loadChargeTypes()
        }
    }

    fun setSelectedMonth(month: String) {
        _selectedMonth.value = month
    }

    fun loadChargeTypes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _chargeTypes.value = repository.getChargeTypesWithStats(_selectedMonth.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSubTypes(typeId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _subTypes.value = repository.getSubTypesWithStats(typeId, _selectedMonth.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCharges(subtypeId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _charges.value = repository.getCharges(subtypeId, _selectedMonth.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addChargeType(
        name      : String,
        icon      : String,
        colorHex  : String,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addChargeType(name, icon, colorHex)
                loadChargeTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun addSubType(
        typeId         : Int,
        name           : String,
        icon           : String,
        hasFournisseur : Boolean,
        onSuccess      : () -> Unit,
        onError        : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addSubType(typeId, name, icon, hasFournisseur)
                loadSubTypes(typeId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun addCharge(
        subtypeId   : Int,
        montant     : Double,
        dateTime    : String,
        fournisseur : String?,
        note        : String?,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addCharge(subtypeId, montant, dateTime, fournisseur, note)
                loadCharges(subtypeId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun updateCharge(
        id          : Int,
        subtypeId   : Int,
        montant     : Double,
        dateTime    : String,
        fournisseur : String?,
        note        : String?,
        onSuccess   : () -> Unit,
        onError     : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateCharge(id, montant, dateTime, fournisseur, note)
                loadCharges(subtypeId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteCharge(
        id        : Int,
        subtypeId : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteCharge(id)
                loadCharges(subtypeId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    companion object {
        fun currentMonth(): String = java.time.LocalDate.now().toString().take(7) // "yyyy-MM"
    }

    fun deleteChargeType(
        id        : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteChargeType(id)
                loadChargeTypes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteSubType(
        id        : Int,
        typeId    : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSubType(id)
                loadSubTypes(typeId)
                loadChargeTypes()   // subtypes_count تغيّر
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }
}