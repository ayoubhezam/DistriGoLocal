package com.distrigo.app.data.billing

import android.app.Activity
import com.distrigo.app.BuildConfig
import java.security.MessageDigest

/**
 * Google Play Billing (docs/play_billing.md), the app's side. Off until the Play Console exists: then nothing here
 * runs and the Subscription screen is as it was. Codes stay the other way to pay, the Algerian one.
 */
object BillingSwitch {
    /** The paywall shows, and Google Play's billing library is started. */
    val enabled: Boolean get() = BuildConfig.BILLING_ENABLED || BuildConfig.BILLING_FAKE

    /** Debug builds only: made-up offers instead of Google Play's, to look at the paywall without a Console. */
    val fake: Boolean get() = BuildConfig.BILLING_FAKE
}

/** What can be bought: the server's CATALOGUE (supabase/functions/_shared/play.ts) names the same. */
object BillingCatalogue {
    const val SOLO = "distroniq_solo"
    const val SOLO_MONTHLY = "solo-monthly"
    const val SOLO_YEARLY = "solo-yearly"
    val PRODUCTS = listOf(SOLO)
}

enum class BillingPeriod { MONTHLY, YEARLY }

/** One base plan as Google Play sells it here: its price already written in the buyer's currency. */
data class PlanOffer(
    val productId: String,
    val basePlanId: String,
    val period: BillingPeriod,
    val formattedPrice: String,
    /** Google's handle for this offer, given back when it is bought. */
    val offerToken: String,
)

sealed interface OffersResult {
    /** The plans on sale, monthly first. */
    data class Offers(val offers: List<PlanOffer>) : OffersResult
    /** Google Play can't sell here and now: no Play Store, not signed in to it, no network, no product yet. */
    data class Unavailable(val reason: BillingUnavailable) : OffersResult
}

enum class BillingUnavailable { NO_PLAY_STORE, NETWORK, NOT_ON_SALE, ERROR }

/** A subscription Google Play holds for the phone's Google account. */
data class OwnedPurchase(
    val productId: String,
    val purchaseToken: String,
    /** Paid (true), or waiting for a payment made outside the app (cash at a shop: false). */
    val purchased: Boolean,
)

sealed interface BuyResult {
    data class Purchased(val purchase: OwnedPurchase) : BuyResult
    /** Accepted, paid later (cash): the server hears of it when it is paid. */
    data object Pending : BuyResult
    data object Cancelled : BuyResult
    /** The Google account already has this subscription: restore it instead. */
    data object AlreadyOwned : BuyResult
    data class Failed(val reason: BillingUnavailable) : BuyResult
}

/** Google Play's billing, as the app needs it; a fake one in tests and in BILLING_FAKE builds. */
interface BillingGateway {
    suspend fun offers(): OffersResult

    /** Opens Google Play's payment sheet for [offer] over [activity] (none: it fails); [accountId] ties the purchase to the business (see [accountIdFor]). */
    suspend fun buy(activity: Activity?, offer: PlanOffer, accountId: String): BuyResult

    /** The subscriptions the Google account already holds, to verify again (a new phone, a reinstall). */
    suspend fun owned(): List<OwnedPurchase>
}

/**
 * The purchase's obfuscatedAccountId: SHA-256 of the business id as text, in hex. The server computes the same
 * (private.play_account_id) and refuses a purchase made for another business. Google never holds our id itself.
 */
fun accountIdFor(businessId: String): String =
    MessageDigest.getInstance("SHA-256").digest(businessId.lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Google Play's page for managing this subscription (cancel, change the payment method). */
fun manageSubscriptionUrl(productId: String, packageName: String): String =
    "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName"
