package com.distrigo.app.ui.account

import com.distrigo.app.data.license.licText
import com.distrigo.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.ActivationCodeStore
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.EmailUpdate
import com.distrigo.app.data.license.ActivationRequest
import com.distrigo.app.data.license.ActivationStep
import com.distrigo.app.data.license.BusinessProfile
import com.distrigo.app.data.license.BusinessProfileStore
import com.distrigo.app.data.license.CheckInLog
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.ProfileAnswer
import com.distrigo.app.data.license.Renewal
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.ui.onboarding.CODE_LENGTH
import com.distrigo.app.ui.onboarding.MIN_PASSWORD
import com.distrigo.app.ui.onboarding.activityCode
import com.distrigo.app.ui.onboarding.isEmail
import com.distrigo.app.ui.onboarding.isPhone
import com.distrigo.app.ui.onboarding.isWellFormedCode
import com.distrigo.app.ui.onboarding.normalizeCode
import com.distrigo.app.ui.onboarding.salesRepsCode
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/** What is under way, so the screens disable their buttons and show a spinner. */
enum class AccountWork { SIGNING_IN, ACTIVATING, RENEWING, ADDING_EMAIL, SAVING_PROFILE, KEEPING_DATA, SIGNING_OUT, DELETING }

/** « Informations de l'entreprise »: each row, under the name business-profile knows it by. */
enum class ProfileField(val server: String, @androidx.annotation.StringRes private val titleId: Int) {
    NAME("name", R.string.acc_business_name),
    PHONE("contact_phone", R.string.acc_phone),
    ACTIVITY("activity", R.string.acc_activity),
    SALES_REPS("sales_reps", R.string.acc_sales_reps);

    val title: String get() = licText(titleId)
}

/** Where reading the business's profile stands. */
enum class ProfileLoad { IDLE, LOADING, LOADED, NO_BUSINESS, FAILED }

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

    /** One row of « Informations de l'entreprise » changed. */
    data class EditProfile(val field: ProfileField, val error: String? = null) : AccountDialog
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
    /** The business's profile: from the server, or the last one kept on the phone; null until known. */
    val profile: BusinessProfile? = null,
    val profileLoad: ProfileLoad = ProfileLoad.IDLE,
    /** Why the profile could not be read, when nothing was kept either. */
    val profileProblem: String? = null,
    /** The background check-ins seem held back by the battery saving (CheckInLog.backgroundHeldBack). */
    val batteryHint: Boolean = false,
    /** The name the data on this phone carries (Paramètres, the receipts' header); null when never set. */
    val dataName: String? = null,
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
    private val checkInLog: CheckInLog,
    private val businessSettings: BusinessSettingsRepository,
    private val codes: ActivationCodeStore,
    private val profiles: BusinessProfileStore,
) : ViewModel() {

    private val local = MutableStateFlow(AccountUiState())

    val state: StateFlow<AccountUiState> =
        combine(auth.state, license.state, local, codes.saved, profiles.saved) { account, licenseState, ui, saved, profile ->
            val userId = (account as? AccountState.SignedIn)?.session?.userId
            ui.copy(
                account = account,
                license = licenseState,
                // Only the business this phone holds: another account signed in here never sees it
                activationCode = saved?.takeIf { userId != null && it.businessId == licenseState.heldLicense?.businessId }?.code,
                profile = profile?.takeIf { userId != null && it.userId == userId }?.profile,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        // The Compte screen shows what the license allows now, not at the last evaluation.
        viewModelScope.launch(Dispatchers.IO) {
            auth.restore()
            license.evaluate()
            codes.load()
            profiles.load()
            readBatteryHint()
            val dataName = runCatching { businessSettings.get().name }.getOrNull()?.takeIf { it.isNotBlank() }
            local.update { it.copy(dataName = dataName) }
            // What changed on the server since the session was issued (the admin flag)
            if (auth.state.value is AccountState.SignedIn) auth.refreshSession()
            withContext(Dispatchers.Main) { refreshProfile() }
        }
        // Someone signed in (in the onboarding, above Compte): « Vous êtes déconnecté. » and its kind no longer
        // apply, and the profile is theirs to read
        viewModelScope.launch {
            auth.state.map { it is AccountState.SignedIn }.distinctUntilChanged().drop(1).collect { signedIn ->
                if (signedIn) {
                    dismissMessage()
                    refreshProfile()
                }
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

    // ── The business ──

    /** The profile from the server, kept for next time. Without Internet the one kept stays on screen. */
    fun refreshProfile() {
        val session = (auth.state.value as? AccountState.SignedIn)?.session ?: return
        if (local.value.profileLoad == ProfileLoad.LOADING) return
        local.value = local.value.copy(profileLoad = ProfileLoad.LOADING, profileProblem = null)
        viewModelScope.launch {
            when (val answer = client.businessProfile()) {
                is ProfileAnswer.Found -> {
                    withContext(Dispatchers.IO) { profiles.save(session.userId, answer.profile) }
                    local.value = local.value.copy(profileLoad = ProfileLoad.LOADED)
                }
                ProfileAnswer.NoBusiness -> {
                    withContext(Dispatchers.IO) { profiles.clear() }
                    local.value = local.value.copy(profileLoad = ProfileLoad.NO_BUSINESS)
                }
                is ProfileAnswer.Failed -> local.value = local.value.copy(profileLoad = ProfileLoad.FAILED, profileProblem = answer.message)
            }
        }
    }

    /** One row of « Informations de l'entreprise »: checked here, sent, then kept as the server answers it. */
    fun saveProfile(field: ProfileField, typed: String) {
        val session = (auth.state.value as? AccountState.SignedIn)?.session ?: return
        val text = typed.trim()
        val value: String? = when (field) {
            ProfileField.NAME -> when {
                text.isEmpty() -> return ask(AccountDialog.EditProfile(field, licText(R.string.acc_enter_business_name)))
                text.length > 120 -> return ask(AccountDialog.EditProfile(field, licText(R.string.acc_max_120)))
                else -> text
            }
            // Empty clears it
            ProfileField.PHONE -> when {
                text.isEmpty() -> null
                !isPhone(text) -> return ask(AccountDialog.EditProfile(field, licText(R.string.acc_invalid_phone)))
                else -> text
            }
            ProfileField.ACTIVITY -> activityCode(text) ?: return ask(AccountDialog.EditProfile(field, licText(R.string.acc_choose_activity)))
            ProfileField.SALES_REPS -> salesRepsCode(text) ?: return ask(AccountDialog.EditProfile(field, licText(R.string.acc_choose_number)))
        }
        work(AccountWork.SAVING_PROFILE, keepDialog = true) {
            when (val answer = client.businessProfile(mapOf(field.server to value))) {
                is ProfileAnswer.Found -> {
                    withContext(Dispatchers.IO) { profiles.save(session.userId, answer.profile) }
                    local.value = local.value.copy(dialog = null, profileLoad = ProfileLoad.LOADED, profileProblem = null)
                }
                ProfileAnswer.NoBusiness -> ask(AccountDialog.EditProfile(field, licText(R.string.acc_no_business)))
                is ProfileAnswer.Failed -> ask(AccountDialog.EditProfile(field, answer.message))
            }
        }
    }

    // ── The license ──

    fun activate() = work(AccountWork.ACTIVATING) { follow(client.startActivation()) }

    fun answerBusinessName(request: ActivationRequest, name: String) =
        work(AccountWork.ACTIVATING) { follow(client.continueActivation(request, businessName = name.trim())) }

    fun confirmTransfer(request: ActivationRequest) =
        work(AccountWork.ACTIVATING) { follow(client.continueActivation(request, transfer = true)) }

    /**
     * « Garder les données pour … » (docs/license_l4_checkin.md §7): the data on this phone becomes the account's
     * business's, an owner who moved to another account say.
     */
    fun keepData() = work(AccountWork.KEEPING_DATA) {
        val state = withContext(Dispatchers.IO) { license.keepData() }
        if (state !is LicenseState.OtherBusiness) {
            local.update { it.copy(message = AccountMessage(licText(R.string.acc_data_kept), isError = false)) }
        }
    }

    /** From the check-in log. File I/O: off the main thread. */
    private fun readBatteryHint() {
        val held = CheckInLog.backgroundHeldBack(checkInLog.read(), System.currentTimeMillis())
        local.update { it.copy(batteryHint = held) }
    }

    /** « Saisir un code »: the dialog stays up while the server works, and shows its refusal under the field. */
    fun renewWithCode(typed: String) {
        val code = normalizeCode(typed)
        if (!isWellFormedCode(code)) {
            ask(AccountDialog.EnterCode(if (code.length < CODE_LENGTH) licText(R.string.acc_code_length, CODE_LENGTH) else TYPO))
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
            password.length < MIN_PASSWORD -> licText(R.string.acc_password_min, MIN_PASSWORD)
            else -> null
        }
        if (problem != null) return ask(AccountDialog.AddEmail(problem))
        work(AccountWork.ADDING_EMAIL, keepDialog = true) {
            when (val update = auth.addEmail(email, password)) {
                EmailUpdate.Done -> {
                    local.value = local.value.copy(dialog = null, message = AccountMessage(licText(R.string.acc_email_added), isError = false))
                }
                is EmailUpdate.ConfirmByLink -> {
                    local.value = local.value.copy(dialog = null, message = AccountMessage(licText(R.string.acc_open_link, update.email), isError = false))
                }
                is EmailUpdate.Failed -> ask(
                    AccountDialog.AddEmail(
                        if (update.error == AuthError.EMAIL_TAKEN) licText(R.string.acc_email_taken) else update.error.message,
                    ),
                )
            }
        }
    }

    private suspend fun follow(step: ActivationStep) {
        when (step) {
            is ActivationStep.Activated -> {
                ask(AccountDialog.Activated(step.trial, step.reduced))
                // A business may have been made for it
                refreshProfile()
            }
            // Only an activation code asks this, and Compte sends none
            is ActivationStep.CodeNeedsBusiness -> showError(licText(R.string.acc_activate_from_home))
            is ActivationStep.NeedsBusinessName -> {
                val suggestion = withContext(Dispatchers.IO) { runCatching { businessSettings.get().name }.getOrDefault("") }
                ask(AccountDialog.BusinessName(step.request, suggestion))
            }
            is ActivationStep.SeatTaken -> ask(AccountDialog.Transfer(step.request, step.holders))
            is ActivationStep.TransferLimit -> ask(AccountDialog.TransferLimit(step.nextAt))
            ActivationStep.AlreadyActivated -> local.value = local.value.copy(message = AccountMessage(licText(R.string.acc_already_activated), isError = false))
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
            local.value = local.value.copy(message = AccountMessage(licText(R.string.acc_signed_out), isError = false))
        }
    }

    fun deleteAccount() = work(AccountWork.DELETING) {
        val problem = client.deleteAccount()
        // Its business is gone with it: the code opens nothing any more
        if (problem == null) {
            withContext(Dispatchers.IO) {
                codes.clear()
                profiles.clear()
            }
        }
        local.value = local.value.copy(
            message = if (problem == null) AccountMessage(licText(R.string.acc_deleted), isError = false)
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

private val TYPO: String get() = licText(R.string.acc_typo)
