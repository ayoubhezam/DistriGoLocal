package com.distrigo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.CostMethod
import com.distrigo.app.data.repository.BusinessSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** « Coût des produits » in Paramètres (FIN-04): how a product's cost follows the bons received. */
@HiltViewModel
class CostMethodSettingsViewModel @Inject constructor(
    private val settings: BusinessSettingsRepository,
) : ViewModel() {

    /** Null while it loads. */
    val method: StateFlow<CostMethod?> =
        settings.observeCostMethod().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(method: CostMethod) {
        viewModelScope.launch { settings.setCostMethod(method) }
    }
}
