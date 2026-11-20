package com.distrigo.app.data.billing

import android.app.Activity

/**
 * Made-up offers at the placeholder prices of docs/play_billing.md, for BILLING_FAKE debug builds (to look at the
 * paywall before the Play Console exists) and for the tests. A purchase « succeeds » with a token Google never made,
 * which the server then refuses: nothing is ever paid or granted.
 */
class FakeBillingGateway(
    private val offersResult: OffersResult = OffersResult.Offers(
        listOf(
            PlanOffer(BillingCatalogue.SOLO, BillingCatalogue.SOLO_MONTHLY, BillingPeriod.MONTHLY, "9,99 US$", "fake-monthly"),
            PlanOffer(BillingCatalogue.SOLO, BillingCatalogue.SOLO_YEARLY, BillingPeriod.YEARLY, "99,99 US$", "fake-yearly"),
        ),
    ),
    var buyResult: (PlanOffer) -> BuyResult = { offer ->
        BuyResult.Purchased(OwnedPurchase(offer.productId, "fake-token-${offer.basePlanId}", purchased = true))
    },
    var ownedPurchases: List<OwnedPurchase> = emptyList(),
) : BillingGateway {
    /** The account id each purchase was launched with, for the tests. */
    val launchedFor = mutableListOf<String>()

    override suspend fun offers(): OffersResult = offersResult

    override suspend fun buy(activity: Activity?, offer: PlanOffer, accountId: String): BuyResult {
        launchedFor += accountId
        return buyResult(offer)
    }

    override suspend fun owned(): List<OwnedPurchase> = ownedPurchases
}
