package com.distrigo.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play's Billing Library (8.x), as [BillingGateway]. Connected on first use, only once BillingSwitch is on.
 * One purchase at a time: Google answers through one listener, which hands the result to the purchase waiting for it.
 */
class PlayBillingGateway(context: Context) : BillingGateway {

    private val connecting = Mutex()
    private var waiting: CompletableDeferred<BuyResult>? = null
    /** The details of the products last listed, which a purchase needs to open the payment sheet. */
    private var details: Map<String, ProductDetails> = emptyMap()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val answer = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().firstOrNull()?.let(::buyResultOf) ?: BuyResult.Cancelled
            BillingClient.BillingResponseCode.USER_CANCELED -> BuyResult.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BuyResult.AlreadyOwned
            else -> BuyResult.Failed(reasonOf(result))
        }
        waiting?.complete(answer)
    }

    // Made on first use: while BillingSwitch is off nothing calls this gateway, so Google Play is never touched
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Connects if needed; null when connected, else why not. */
    private suspend fun connect(): BillingUnavailable? = connecting.withLock {
        if (client.isReady) return null
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) null else reasonOf(result))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(BillingUnavailable.NETWORK)
                }
            })
        }
    }

    override suspend fun offers(): OffersResult {
        connect()?.let { return OffersResult.Unavailable(it) }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingCatalogue.PRODUCTS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
                },
            )
            .build()
        val (result, products) = suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, answer ->
                if (continuation.isActive) continuation.resume(result to answer.productDetailsList)
            }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return OffersResult.Unavailable(reasonOf(result))
        details = products.associateBy { it.productId }
        val offers = products.flatMap { product ->
            product.subscriptionOfferDetails.orEmpty()
                // The base plans only: no Play trial or promotional offer (the trial is ours, on the server)
                .filter { it.offerId == null }
                .mapNotNull { offer ->
                    val period = when (offer.basePlanId) {
                        BillingCatalogue.SOLO_MONTHLY -> BillingPeriod.MONTHLY
                        BillingCatalogue.SOLO_YEARLY -> BillingPeriod.YEARLY
                        else -> return@mapNotNull null
                    }
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
                    PlanOffer(product.productId, offer.basePlanId, period, price, offer.offerToken)
                }
        }.sortedBy { it.period }
        return if (offers.isEmpty()) OffersResult.Unavailable(BillingUnavailable.NOT_ON_SALE) else OffersResult.Offers(offers)
    }

    override suspend fun buy(activity: Activity?, offer: PlanOffer, accountId: String): BuyResult {
        if (activity == null) return BuyResult.Failed(BillingUnavailable.ERROR)
        connect()?.let { return BuyResult.Failed(it) }
        val product = details[offer.productId] ?: return BuyResult.Failed(BillingUnavailable.NOT_ON_SALE)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()),
            )
            .setObfuscatedAccountId(accountId)
            .build()
        val answer = CompletableDeferred<BuyResult>()
        waiting = answer
        val launched = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
            waiting = null
            return if (launched.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) BuyResult.AlreadyOwned
            else BuyResult.Failed(reasonOf(launched))
        }
        return answer.await().also { waiting = null }
    }

    override suspend fun owned(): List<OwnedPurchase> {
        if (connect() != null) return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                continuation.resume(
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) emptyList()
                    else purchases.mapNotNull { purchase -> ownedOf(purchase) },
                )
            }
        }
    }

    private fun ownedOf(purchase: Purchase): OwnedPurchase? {
        val product = purchase.products.firstOrNull { it in BillingCatalogue.PRODUCTS } ?: return null
        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> OwnedPurchase(product, purchase.purchaseToken, purchased = true)
            Purchase.PurchaseState.PENDING -> OwnedPurchase(product, purchase.purchaseToken, purchased = false)
            else -> null
        }
    }

    private fun buyResultOf(purchase: Purchase): BuyResult = when (val owned = ownedOf(purchase)) {
        null -> BuyResult.Failed(BillingUnavailable.ERROR)
        else -> if (owned.purchased) BuyResult.Purchased(owned) else BuyResult.Pending
    }

    private fun reasonOf(result: BillingResult): BillingUnavailable = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingUnavailable.NO_PLAY_STORE
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingUnavailable.NETWORK
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingUnavailable.NOT_ON_SALE
        else -> BillingUnavailable.ERROR
    }
}
