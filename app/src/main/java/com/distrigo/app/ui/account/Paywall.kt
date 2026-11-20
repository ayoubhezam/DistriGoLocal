package com.distrigo.app.ui.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.R
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.billing.BillingGateway
import com.distrigo.app.data.billing.BillingPeriod
import com.distrigo.app.data.billing.BillingSwitch
import com.distrigo.app.data.billing.BillingUnavailable
import com.distrigo.app.data.billing.BuyResult
import com.distrigo.app.data.billing.OffersResult
import com.distrigo.app.data.billing.OwnedPurchase
import com.distrigo.app.data.billing.PlanOffer
import com.distrigo.app.data.billing.accountIdFor
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.PlayVerification
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.licText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the paywall is (docs/play_billing.md §3). */
sealed interface PaywallStep {
    data object Loading : PaywallStep

    /** Nobody signed in, or no license held on this phone: sign in (or activate) first. */
    data object SignInFirst : PaywallStep

    /** An account made by a code: an e-mail first, or the purchase dies with the account on an uninstall. */
    data object NeedsEmail : PaywallStep

    /**
     * The plans on sale, one chosen. [owned]: the Google account already pays for one, so « Gérer mon abonnement »
     * shows instead of « S'abonner ». [busy]: Google's sheet is open, or the purchase is being checked.
     */
    data class Choose(
        val offers: List<PlanOffer>,
        val selected: BillingPeriod,
        val owned: OwnedPurchase? = null,
        val busy: Boolean = false,
    ) : PaywallStep

    /** Google Play can't sell here and now. */
    data class Unavailable(val reason: BillingUnavailable) : PaywallStep
}

data class PaywallMessage(val text: String, val isError: Boolean)

data class PaywallState(val step: PaywallStep = PaywallStep.Loading, val message: PaywallMessage? = null)

/** Who is asking: whether an account is signed in, made by a code, and which business the phone's license is for. */
data class PaywallBuyer(val signedIn: Boolean, val anonymous: Boolean, val businessId: String?)

/**
 * The paywall's rules, without Android: what to show for a buyer, a purchase from Google to the server, and a
 * restore of what the Google account already holds. [verify] is LicenseClient.verifyPlayPurchase.
 */
class Paywall(
    private val gateway: BillingGateway,
    private val verify: suspend (purchaseToken: String, productId: String) -> PlayVerification,
) {
    /** The first screen for [buyer]; purchases the Google account already holds are sent to the server again. */
    suspend fun open(buyer: PaywallBuyer): PaywallState {
        if (!buyer.signedIn || buyer.businessId == null) return PaywallState(PaywallStep.SignInFirst)
        if (buyer.anonymous) return PaywallState(PaywallStep.NeedsEmail)
        val offers = when (val result = gateway.offers()) {
            is OffersResult.Offers -> result.offers
            is OffersResult.Unavailable -> return PaywallState(PaywallStep.Unavailable(result.reason))
        }
        val owned = gateway.owned().firstOrNull { it.purchased }
        // A restore: a new phone or a reinstall finds the subscription it pays for. Quiet unless it fails for good.
        val message = owned?.let { restoreMessage(verify(it.purchaseToken, it.productId)) }
        return PaywallState(PaywallStep.Choose(offers, offers.first().period, owned), message)
    }

    /** Buys the chosen plan over [activity], then has the server record it; [state] is the paywall as it stands. */
    suspend fun buy(state: PaywallState, activity: Activity?, buyer: PaywallBuyer, onBusy: (PaywallState) -> Unit): PaywallState {
        val choose = state.step as? PaywallStep.Choose ?: return state
        val businessId = buyer.businessId ?: return PaywallState(PaywallStep.SignInFirst)
        if (buyer.anonymous) return PaywallState(PaywallStep.NeedsEmail)
        val offer = choose.offers.firstOrNull { it.period == choose.selected } ?: return state
        onBusy(PaywallState(choose.copy(busy = true)))
        return when (val bought = gateway.buy(activity, offer, accountIdFor(businessId))) {
            is BuyResult.Purchased -> afterVerify(choose, bought.purchase, verify(bought.purchase.purchaseToken, bought.purchase.productId))
            BuyResult.Pending -> PaywallState(choose, PaywallMessage(licText(R.string.bill_pending), isError = false))
            BuyResult.Cancelled -> PaywallState(choose)
            BuyResult.AlreadyOwned -> {
                val owned = gateway.owned().firstOrNull { it.purchased }
                    ?: return PaywallState(choose, PaywallMessage(licText(R.string.bill_unavailable_error), isError = true))
                afterVerify(choose, owned, verify(owned.purchaseToken, owned.productId))
            }
            is BuyResult.Failed -> PaywallState(choose, PaywallMessage(unavailableText(bought.reason), isError = true))
        }
    }

    private fun afterVerify(choose: PaywallStep.Choose, purchase: OwnedPurchase, verification: PlayVerification): PaywallState =
        when (verification) {
            PlayVerification.Active -> PaywallState(choose.copy(owned = purchase, busy = false), PaywallMessage(licText(R.string.bill_active), isError = false))
            PlayVerification.EmailRequired -> PaywallState(PaywallStep.NeedsEmail)
            is PlayVerification.Failed -> PaywallState(
                choose.copy(owned = purchase, busy = false),
                PaywallMessage(if (verification.retry) licText(R.string.bill_paid_not_yet, verification.message) else verification.message, isError = true),
            )
        }

    private fun restoreMessage(verification: PlayVerification): PaywallMessage? = when (verification) {
        PlayVerification.Active -> null
        PlayVerification.EmailRequired -> null
        is PlayVerification.Failed -> if (verification.retry) null else PaywallMessage(verification.message, isError = true)
    }

    companion object {
        fun unavailableText(reason: BillingUnavailable): String = licText(
            when (reason) {
                BillingUnavailable.NO_PLAY_STORE -> R.string.bill_unavailable_store
                BillingUnavailable.NETWORK -> R.string.bill_unavailable_network
                BillingUnavailable.NOT_ON_SALE -> R.string.bill_unavailable_not_on_sale
                BillingUnavailable.ERROR -> R.string.bill_unavailable_error
            },
        )
    }
}

/** The Subscription screen's paywall: only when BillingSwitch is on; off, it never touches Google Play. */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val gateway: BillingGateway,
    private val client: LicenseClient,
    private val auth: AuthRepository,
    private val license: LicenseManager,
) : ViewModel() {

    val enabled: Boolean = BillingSwitch.enabled
    private val paywall = Paywall(gateway, client::verifyPlayPurchase)
    private val _state = MutableStateFlow(PaywallState())
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    private fun buyer(): PaywallBuyer {
        val session = (auth.state.value as? AccountState.SignedIn)?.session
        return PaywallBuyer(session != null, session?.isAnonymous == true, license.state.value.heldLicense?.businessId)
    }

    /** Reads the offers again: on opening, and once an e-mail was added. */
    fun open() {
        if (!enabled) return
        viewModelScope.launch { _state.value = paywall.open(buyer()) }
    }

    fun select(period: BillingPeriod) {
        val choose = _state.value.step as? PaywallStep.Choose ?: return
        if (!choose.busy) _state.value = PaywallState(choose.copy(selected = period))
    }

    fun buy(activity: Activity?) {
        val current = _state.value
        if ((current.step as? PaywallStep.Choose)?.busy != false) return
        viewModelScope.launch { _state.value = paywall.buy(current, activity, buyer()) { _state.value = it } }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
