package com.distrigo.app.ui.settings.incentive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.dao.incentive.PolicyWithTiers
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity
import com.distrigo.app.data.repository.IncentiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IncentiveViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = IncentiveRepository(db)

    private val _activePolicy = MutableStateFlow<PolicyWithTiers?>(null)
    val activePolicy: StateFlow<PolicyWithTiers?> = _activePolicy.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init { loadActivePolicy() }

    fun loadActivePolicy() {
        viewModelScope.launch {
            _isLoading.value = true
            _activePolicy.value = repository.getActivePolicyWithTiers()
            _isLoading.value = false
        }
    }

    fun savePolicy(
        policy: TargetPolicyEntity,
        tiers: List<PolicyTierEntity>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.savePolicy(policy, tiers)
                loadActivePolicy()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            } finally {
                _isSaving.value = false
            }
        }
    }
}