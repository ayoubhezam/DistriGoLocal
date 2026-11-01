package com.distrigo.app.ui.onboarding

import com.distrigo.app.data.license.licText
import com.distrigo.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.ActivationCodeStore
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthOutcome
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.license.ActivationDetails
import com.distrigo.app.data.license.ActivationRequest
import com.distrigo.app.data.license.ActivationStep
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.Renewal
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.holdsLicense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The onboarding's account side (docs/activation_codes.md, step C2): signs in, signs up or takes a code, then
 * activates this phone, and reports each step as [OnboardingStatus] for the screens to show. One instance for the
 * activity; [reset] each time the onboarding opens.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val client: LicenseClient,
    private val codes: ActivationCodeStore,
) : ViewModel() {

    private val _status = MutableStateFlow(OnboardingStatus())
    val status: StateFlow<OnboardingStatus> = _status.asStateFlow()

    /** The activation waiting for the user's answer (a company, a transfer), and how it began. */
    private var pending: ActivationRequest? = null
    private var origin = ActivationOrigin.SIGN_IN

    /** The code sent with the activation under way: kept as the business key if it creates or joins one. */
    private var activationCode: String? = null

    /** A fresh start: the onboarding opens with no answer pending. */
    fun reset() {
        pending = null
        activationCode = null
        _status.value = OnboardingStatus()
    }

    fun signIn(email: String, password: String) = work {
        whenSignedIn(auth.signIn(email, password)) { activate(ActivationOrigin.SIGN_IN) }
    }

    fun signInWithGoogle(idToken: String, rawNonce: String) = work {
        whenSignedIn(auth.signInWithGoogle(idToken, rawNonce)) { activate(ActivationOrigin.SIGN_IN) }
    }

    /** A new e-mail account, then this phone activated in its new business, with its trial. */
    fun createAccount(form: SignUpForm) = work {
        whenSignedIn(auth.signUp(form.email, form.password, form.fullName)) {
            activate(
                ActivationOrigin.NEW_ACCOUNT,
                ActivationDetails(
                    businessName = form.company,
                    contactPhone = form.phone,
                    activity = activityCode(form.activity),
                    salesReps = salesRepsCode(form.salesReps),
                ),
            )
        }
    }

    /**
     * A code. With a license already here (a trial that ended), the code pays for its business; otherwise it
     * activates the phone, under whoever is signed in, or under a new account without an e-mail.
     */
    fun redeemCode(code: String) = work {
        origin = ActivationOrigin.CODE
        if (license.state.value.holdsLicense) {
            when (val renewal = client.renewWithCode(code)) {
                Renewal.Renewed -> _status.value = activated(code = "renewed")
                is Renewal.Failed -> fail(renewal.message)
            }
            return@work
        }
        if (auth.state.value !is AccountState.SignedIn) {
            when (val outcome = auth.signInAnonymously()) {
                is AuthOutcome.SignedIn -> Unit
                is AuthOutcome.Failed -> return@work fail(outcome.error.message)
                is AuthOutcome.ConfirmEmail -> return@work fail(AuthError.SERVER.message)
            }
        }
        activate(ActivationOrigin.CODE, ActivationDetails(activationCode = code))
    }

    /** The company the server asked for: a code's that names none, or an account's without a business. */
    fun completeBusiness(company: String, phone: String) = work {
        val request = pending ?: return@work fail(EXPIRED)
        follow(client.continueActivation(request, businessName = company, contactPhone = phone))
    }

    /** Yes to « move DistriGo to this phone »: the other phone loses its seat (3 moves per 30 days). */
    fun confirmTransfer() = work {
        val request = pending ?: return@work fail(EXPIRED)
        follow(client.continueActivation(request, transfer = true))
    }

    private suspend fun activate(origin: ActivationOrigin, details: ActivationDetails = ActivationDetails()) {
        this.origin = origin
        activationCode = details.activationCode
        follow(client.startActivation(details))
    }

    private suspend fun whenSignedIn(outcome: AuthOutcome, then: suspend () -> Unit) {
        when (outcome) {
            is AuthOutcome.SignedIn -> then()
            is AuthOutcome.Failed -> fail(outcome.error.message)
            is AuthOutcome.ConfirmEmail -> fail(licText(R.string.onb_confirm_email, outcome.email))
        }
    }

    private suspend fun follow(step: ActivationStep) {
        _status.value = when (step) {
            is ActivationStep.Activated -> {
                pending = null
                keepKey(step.code)
                activated(step.code)
            }
            is ActivationStep.NeedsBusinessName -> {
                pending = step.request
                OnboardingStatus(needsBusiness = true)
            }
            is ActivationStep.CodeNeedsBusiness -> {
                pending = step.request
                OnboardingStatus(needsBusiness = true)
            }
            is ActivationStep.SeatTaken -> {
                pending = step.request
                OnboardingStatus(seatTaken = step.holders.map { it.model ?: licText(R.string.acc_another_phone) })
            }
            is ActivationStep.TransferLimit -> OnboardingStatus(error = transferLimit(step.nextAt))
            ActivationStep.AlreadyActivated -> activated(code = null)
            // Kept: the same answer may be sent again once the problem is fixed
            is ActivationStep.Failed -> OnboardingStatus(error = step.message, needsBusiness = _status.value.needsBusiness)
        }
    }

    /**
     * The license just installed, for the success screen. An e-mail sign-up on a phone that already had its trial
     * gets one that has already ended: a code pays for it, so the code screen comes next.
     */
    private fun activated(code: String?): OnboardingStatus {
        val held = license.state.value.heldLicense
            ?: return OnboardingStatus(error = licText(R.string.onb_license_unreadable))
        if (code == null && held.validTo < System.currentTimeMillis()) return OnboardingStatus(codeRequired = true)
        return OnboardingStatus(
            activation = ActivationSummary(
                origin = if (code == "joined") ActivationOrigin.SIGN_IN else origin,
                trial = held.plan == "trial",
                validFromMillis = held.validFrom,
                validToMillis = held.validTo,
            ),
        )
    }

    /**
     * A code that created or joined the business is its key: kept, encrypted, so Compte can show it again (the
     * server holds only its fingerprint). A code that renewed is spent and kept nowhere.
     */
    private suspend fun keepKey(use: String?) {
        val code = activationCode ?: return
        val businessId = license.state.value.heldLicense?.businessId ?: return
        if (use == "created" || use == "joined") {
            withContext(Dispatchers.IO) { runCatching { codes.save(normalizeCode(code), businessId) } }
        }
    }

    /** Leaves the needs-business step in place: the company form stays up with its error. */
    private fun fail(message: String) {
        _status.value = OnboardingStatus(error = message, needsBusiness = _status.value.needsBusiness)
    }

    /** One request at a time; whatever ends it says so in [status]. */
    private fun work(block: suspend () -> Unit) {
        if (_status.value.busy) return
        _status.value = _status.value.copy(busy = true, error = null, seatTaken = null)
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(licText(R.string.onb_error))
            }
            if (_status.value.busy) _status.value = _status.value.copy(busy = false)
        }
    }

    private companion object {
        val EXPIRED: String get() = licText(R.string.onb_expired)
        val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.formatter("d MMMM")

        fun transferLimit(nextAt: Instant?): String =
            nextAt?.let { licText(R.string.onb_transfer_limit_next, DAY.format(it.atZone(ZoneId.systemDefault()))) }
                ?: licText(R.string.onb_transfer_limit)
    }
}
