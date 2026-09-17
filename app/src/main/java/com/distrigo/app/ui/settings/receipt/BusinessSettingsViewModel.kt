package com.distrigo.app.ui.settings.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.BusinessSettings
import com.distrigo.app.data.repository.BusinessSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The business identity for the screens that print it (receipts) and edit it (Paramètres du reçu).
 *
 * The settings live in the database now, which cannot be read on the main thread, so screens hold them
 * as state: [settings] is null for the moment it takes to load, then follows every change.
 */
@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
    private val repository: BusinessSettingsRepository
) : ViewModel() {

    val settings: StateFlow<BusinessSettings?> =
        repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun saveIdentity(name: String, phone: String) = repository.saveIdentity(name, phone)

    /** False if the picked image could not be read or stored; the current logo then stays. */
    suspend fun saveLogo(uri: Uri): Boolean = repository.saveLogo(uri)
}
