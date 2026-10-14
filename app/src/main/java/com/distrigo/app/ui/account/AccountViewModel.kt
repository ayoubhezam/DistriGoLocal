package com.distrigo.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.ActivationCodeStore
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.EmailUpdate
import com.distrigo.app.data.license.ActivationRequest
import com.distrigo.app.data.license.ActivationStep
import com.distrigo.app.data.license.CheckIn
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.Renewal
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.ui.onboarding.CODE_LENGTH
import com.distrigo.app.ui.onboarding.MIN_PASSWORD
import com.distrigo.app.ui.onboarding.isEmail
import com.distrigo.app.ui.onboarding.isWellFormedCode
import com.distrigo.app.ui.onboarding.normalizeCode
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
enum class AccountWork { SIGNING_IN, ACTIVATING, CHECKING_IN, RENEWING, ADDING_EMAIL, SIGNING_OUT, DELETING }

/** A question or an answer shown over the Compte screen. */
sealed interface AccountDialog {
    data class BusinessName(val request: ActivationRequest, val suggestion: String) : AccountDialog
    data class Transfer(val request: ActivationRequest, val holders: List<SeatHolder>) : AccountDialog
    data class TransferLimit(val nextAt: Instant?) : AccountDialog
    data class Activated(val trial: Boolean?, val reduced: Boolean) : AccountDialog

    /** « Saisir un code »: a new code's days for the business. [error]: the last try's, shown under the field. */
    data class EnterCode(val error: String? = null) : AccountDialog
    data class Renewed(val validTo: Long?) : AccountDialog

    /** « Ajouter une adresse e-mail », for an account made by a code. */
    data class AddEmail(val error: String? = null) : AccountDialog
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
    /** This business's activation code, kept on the phone when it opened the business; null when unknown. */
    val activationCode: String? = null,
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
    private val codes: ActivationCodeStore,
) : ViewModel() {

    private val local = MutableStateFlow(AccountUiState())

    val state: StateFlow<AccountUiState> =
        combine(auth.state, license.state, local, codes.saved) { account, licenseState, ui, saved ->
            ui.copy(
                account = account,
                license = licenseState,
                // Only the business this phone holds: another account signed in here never sees it
                activationCode = saved?.takeIf { account is AccountState.SignedIn && it.businessId == licenseState.heldLicense?.businessId }?.code,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        // The Compte screen shows what the license allows now, not at the last evaluation.
        viewModelScope.launch(Dispatchers.IO) {
            auth.restore()
            license.evaluate()
            codes.load()
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

    /** « Saisir un code »: the dialog stays up while the server works, and shows its refusal under the field. */
    fun renewWithCode(typed: String) {
        val code = normalizeCode(typed)
        if (!isWellFormedCode(code)) {
            ask(AccountDialog.EnterCode(if (code.length < CODE_LENGTH) "Le code contient $CODE_LENGTH caractères." else TYPO))
            return
        }
        work(AccountWork.RENEWING, keepDialog = true) {
            when (val renewal = client.renewWithCode(code)) {
                Renewal.Renewed -> ask(AccountDialog.Renewed(license.state.value.heldLicense?.validTo))
                is Renewal.Failed -> ask(AccountDialog.EnterCode(renewal.message))
            }
        }
    }

    // ── The account ──

    /** « Ajouter une adresse e-mail »: the code account becomes an e-mail account, same business, same license. */
    fun addEmail(email: String, password: String) {
        val problem = when {
            !isEmail(email.trim()) -> AuthError.INVALID_EMAIL.message
            password.length < MIN_PASSWORD -> "Le mot de passe doit contenir au moins $MIN_PASSWORD caractères."
            else -> null
        }
        if (problem != null) return ask(AccountDialog.AddEmail(problem))
        work(AccountWork.ADDING_EMAIL, keepDialog = true) {
            when (val update = auth.addEmail(email, password)) {
                EmailUpdate.Done -> {
                    local.value = local.value.copy(dialog = null, message = AccountMessage("Adresse e-mail ajoutée. Vous pouvez aussi vous connecter avec elle.", isError = false))
                }
                is EmailUpdate.ConfirmByLink -> {
                    local.value = local.value.copy(dialog = null, message = AccountMessage("Ouvrez le lien envoyé à ${update.email} pour confirmer l'adresse.", isError = false))
                }
                is EmailUpdate.Failed -> ask(
                    AccountDialog.AddEmail(
                        if (update.error == AuthError.EMAIL_TAKEN) "Cette adresse est déjà celle d'un autre compte." else update.error.message,
                    ),
                )
            }
        }
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
        // Its business is gone with it: the code opens nothing any more
        if (problem == null) withContext(Dispatchers.IO) { codes.clear() }
        local.value = local.value.copy(
            message = if (problem == null) AccountMessage("Votre compte a été supprimé.", isError = false)
            else AccountMessage(problem, isError = true),
        )
    }

    /** One request at a time. [keepDialog]: a dialog with a form stays up, busy, and shows what went wrong. */
    private fun work(kind: AccountWork, keepDialog: Boolean = false, block: suspend () -> Unit) {
        if (local.value.work != null) return
        local.value = local.value.copy(work = kind, dialog = if (keepDialog) local.value.dialog else null, message = null)
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.value = local.value.copy(work = null)
            }
        }
    }
}

private const val TYPO = "Ce code contient une faute de frappe. Vérifiez chaque caractère."
