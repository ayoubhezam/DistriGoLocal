package com.distrigo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.data.repository.BusinessSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Format des montants" in Paramètres: how every screen, receipt and PDF writes an amount. */
@HiltViewModel
class MoneyFormatSettingsViewModel @Inject constructor(
    private val settings: BusinessSettingsRepository,
) : ViewModel() {

    /** Null while it loads. */
    val format: StateFlow<MoneyFormat?> =
        settings.observeMoneyFormat().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(format: MoneyFormat) {
        viewModelScope.launch { settings.setMoneyFormat(format) }
    }
}
