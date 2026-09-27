package com.distrigo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Autoriser le stock négatif" in Paramètres.
 *
 * Turning it off while products are already below zero in the dépôt asks first, with their number:
 * from then on those products cannot leave the dépôt until they are restocked, and the user should
 * know why before a sale refuses them.
 */
@HiltViewModel
class StockSettingsViewModel @Inject constructor(
    private val settings: BusinessSettingsRepository,
    private val products: ProductRepository,
) : ViewModel() {

    /** Null while it loads. */
    val allowNegative: StateFlow<Boolean?> =
        settings.observeAllowNegativeStock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingStrict = MutableStateFlow<Int?>(null)
    /** The number of products already negative, while turning strict stock on waits for the user's yes. */
    val pendingStrict: StateFlow<Int?> = _pendingStrict.asStateFlow()

    fun setAllowNegative(allow: Boolean) {
        viewModelScope.launch {
            if (allow) {
                settings.setAllowNegativeStock(true)
                return@launch
            }
            val negative = products.countNegativeDepotProducts()
            if (negative > 0) _pendingStrict.value = negative else settings.setAllowNegativeStock(false)
        }
    }

    fun confirmStrict() {
        _pendingStrict.value = null
        viewModelScope.launch { settings.setAllowNegativeStock(false) }
    }

    fun cancelStrict() {
        _pendingStrict.value = null
    }
}
