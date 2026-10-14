package com.distrigo.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.license.ActivationRequest
import com.distrigo.app.data.license.ActivationStep
import com.distrigo.app.data.license.CheckIn
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.data.license.holdsLicense
import com.distrigo.app.data.repository.BusinessSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/** What is under way, so the screens disable their buttons and show a spinner. */
enum class AccountWork { SIGNING_IN, ACTIVATING, CHECKING_IN, SIGNING_OUT, DELETING }

/** A question or an answer shown over the Compte screen. */
sealed interface AccountDialog {
    data class BusinessName(val request: ActivationRequest, val suggestion: String) : AccountDialog
    data class Transfer(val request: ActivationRequest, val holders: List<SeatHolder>) : AccountDialog
    data class TransferLimit(val nextAt: Instant?) : AccountDialog
    data class Activated(val trial: Boolean?, val reduced: Boolean) : AccountDialog
    data object ConfirmSignOut : AccountDialog
    data object ConfirmDelete : AccountDialog
}

data class AccountMessage(val text: String, val isError: Boolean)

data class AccountUiState(
    val account: AccountState = AccountState.Loading,
    val license: LicenseState = LicenseState.Unchecked,
    val work: AccountWork? = null,
    val dialog: AccountDialog? = null,
    val message: AccountMessage? = null,
)

/**
 * The account and the license, for the Compte screen and the sign-in and sign-up screens (one instance for the
 * three, scoped to their navigation graph). docs/license_architecture.md §7.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val client: LicenseClient,
    private val businessSettings: BusinessSettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(AccountUiState())

    val state: StateFlow<AccountUiState> =
        combine(auth.state, license.state, local) { account, licenseState, ui ->
            ui.copy(account = account, license = licenseState)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        // The Compte screen shows what the license allows now, not at the last evaluation.
        viewModelScope.launch(Dispatchers.IO) {
            auth.restore()
            license.evaluate()
        }
        // Someone signed in (in the onboarding, above Compte): « Vous êtes déconnecté. » and its kind no longer apply.
        viewModelScope.launch {
            auth.state.map { it is AccountState.SignedIn }.distinctUntilChanged().drop(1).collect { signedIn ->
                if (signedIn) dismissMessage()
            }
        }
    }

    fun dismissDialog() = local.value.let { local.value = it.copy(dialog = null) }

    fun dismissMessage() = local.value.let { local.value = it.copy(message = null) }

    fun ask(dialog: AccountDialog) {
        local.value = local.value.copy(dialog = dialog)
    }

    // Signing in and up is the onboarding's (ui/onboarding/OnboardingViewModel).

    fun showError(text: String) {
        local.value = local.value.copy(message = AccountMessage(text, isError = true))
    }

    // ── The license ──

    fun activate() = work(AccountWork.ACTIVATING) { follow(client.startActivation()) }

    fun answerBusinessName(request: ActivationRequest, name: String) =
        work(AccountWork.ACTIVATING) { follow(client.continueActivation(request, businessName = name.trim())) }

    fun confirmTransfer(request: ActivationRequest) =
        work(AccountWork.ACTIVATING) { follow(client.continueActivation(request, transfer = true)) }

    fun checkIn() = work(AccountWork.CHECKING_IN) {
        local.value = local.value.copy(
            message = when (val result = client.checkIn()) {
                CheckIn.Done -> AccountMessage("Abonnement vérifié.", isError = false)
                CheckIn.Revoked -> AccountMessage("Cet appareil n'est plus associé à l'abonnement.", isError = true)
                CheckIn.NotActivated -> AccountMessage("Activez d'abord cet appareil.", isError = true)
                is CheckIn.Failed -> AccountMessage(result.message, isError = true)
            },
        )
    }

    private suspend fun follow(step: ActivationStep) {
        when (step) {
            is ActivationStep.Activated -> ask(AccountDialog.Activated(step.trial, step.reduced))
            // Only an activation code asks this, and Compte sends none
            is ActivationStep.CodeNeedsBusiness -> showError("Activez ce téléphone avec votre code depuis l'écran d'accueil.")
            is ActivationStep.NeedsBusinessName -> {
                val suggestion = withContext(Dispatchers.IO) { runCatching { businessSettings.get().name }.getOrDefault("") }
                ask(AccountDialog.BusinessName(step.request, suggestion))
            }
            is ActivationStep.SeatTaken -> ask(AccountDialog.Transfer(step.request, step.holders))
            is ActivationStep.TransferLimit -> ask(AccountDialog.TransferLimit(step.nextAt))
            ActivationStep.AlreadyActivated -> local.value = local.value.copy(message = AccountMessage("Cet appareil est déjà activé.", isError = false))
            is ActivationStep.Failed -> showError(step.message)
        }
    }

    // ── Leaving ──

    /** Frees this phone's seat, forgets its license, then signs out. Needs the network, to free the seat. */
    fun signOut() = work(AccountWork.SIGNING_OUT) {
        val problem = client.releaseThisDevice()
        if (problem != null) {
            showError(problem)
        } else {
            auth.signOut()
            local.value = local.value.copy(message = AccountMessage("Vous êtes déconnecté.", isError = false))
        }
    }

    fun deleteAccount() = work(AccountWork.DELETING) {
        val problem = client.deleteAccount()
        local.value = local.value.copy(
            message = if (problem == null) AccountMessage("Votre compte a été supprimé.", isError = false)
            else AccountMessage(problem, isError = true),
        )
    }

    private fun work(kind: AccountWork, block: suspend () -> Unit) {
        if (local.value.work != null) return
        local.value = local.value.copy(work = kind, dialog = null, message = null)
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.value = local.value.copy(work = null)
            }
        }
    }
}
