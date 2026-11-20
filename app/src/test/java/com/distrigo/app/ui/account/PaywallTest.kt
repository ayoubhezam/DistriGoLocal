package com.distrigo.app.ui.account

import com.distrigo.app.data.billing.BillingPeriod
import com.distrigo.app.data.billing.BillingUnavailable
import com.distrigo.app.data.billing.BuyResult
import com.distrigo.app.data.billing.FakeBillingGateway
import com.distrigo.app.data.billing.OffersResult
import com.distrigo.app.data.billing.OwnedPurchase
import com.distrigo.app.data.billing.accountIdFor
import com.distrigo.app.data.license.PlayVerification
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The paywall's rules (docs/play_billing.md §3), with a fake Google Play and a fake server. */
class PaywallTest {

    private val previousLocale = java.util.Locale.getDefault()
    @Before fun french() { com.distrigo.app.testing.FrenchStrings.install(); java.util.Locale.setDefault(java.util.Locale.FRENCH) }
    @After fun restore() { com.distrigo.app.testing.FrenchStrings.uninstall(); java.util.Locale.setDefault(previousLocale) }

    private val business = "eba8a58d-6c54-40a9-b6d9-7947a599a12f"
    private val owner = PaywallBuyer(signedIn = true, anonymous = false, businessId = business)
    private val play = FakeBillingGateway()
    private val verified = mutableListOf<String>()
    private var answer: PlayVerification = PlayVerification.Active
    private val paywall = Paywall(play) { token, _ -> verified += token; answer }

    private fun open(buyer: PaywallBuyer = owner) = runBlocking { paywall.open(buyer) }
    private fun buy(state: PaywallState, buyer: PaywallBuyer = owner) = runBlocking { paywall.buy(state, null, buyer) {} }

    @Test
    fun `the purchase carries the business as the server hashes it`() {
        // private.play_account_id: encode(extensions.digest(id::text, 'sha256'), 'hex')
        assertEquals("61d01029a362bef15cdf71e06a1102f0cc24c243e906a4e153c551ea3884e6b5", accountIdFor(business))
        assertEquals(accountIdFor(business), accountIdFor(business.uppercase()))
    }

    @Test
    fun `nobody signed in, or no license on the phone, signs in first`() {
        assertEquals(PaywallStep.SignInFirst, open(PaywallBuyer(signedIn = false, anonymous = false, businessId = business)).step)
        assertEquals(PaywallStep.SignInFirst, open(owner.copy(businessId = null)).step)
    }

    @Test
    fun `an account made by a code adds an e-mail before any plan shows, and can't buy`() {
        val coded = owner.copy(anonymous = true)
        assertEquals(PaywallStep.NeedsEmail, open(coded).step)
        val offers = open()
        assertEquals(PaywallStep.NeedsEmail, buy(offers, coded).step)
        assertTrue(play.launchedFor.isEmpty())
    }

    @Test
    fun `the plans show monthly first, chosen, with Google Play's prices`() {
        val step = open().step as PaywallStep.Choose
        assertEquals(listOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY), step.offers.map { it.period })
        assertEquals(listOf("9,99 US$", "99,99 US$"), step.offers.map { it.formattedPrice })
        assertEquals(BillingPeriod.MONTHLY, step.selected)
        assertNull(step.owned)
    }

    @Test
    fun `without Google Play the paywall says why`() {
        val paywall = Paywall(FakeBillingGateway(offersResult = OffersResult.Unavailable(BillingUnavailable.NO_PLAY_STORE))) { _, _ -> answer }
        assertEquals(PaywallStep.Unavailable(BillingUnavailable.NO_PLAY_STORE), runBlocking { paywall.open(owner) }.step)
    }

    @Test
    fun `a purchase is made for the business, verified by the server, then managed in Google Play`() {
        val state = open()
        val chosen = PaywallState((state.step as PaywallStep.Choose).copy(selected = BillingPeriod.YEARLY))
        val after = buy(chosen)
        assertEquals(listOf(accountIdFor(business)), play.launchedFor)
        assertEquals(listOf("fake-token-solo-yearly"), verified)
        val step = after.step as PaywallStep.Choose
        assertEquals("fake-token-solo-yearly", step.owned?.purchaseToken)
        assertEquals(false, step.busy)
        assertEquals(PaywallMessage("Abonnement activé. Merci !", isError = false), after.message)
    }

    @Test
    fun `the button is busy while Google's sheet is open`() {
        val busy = mutableListOf<PaywallState>()
        runBlocking { paywall.buy(open(), null, owner) { busy += it } }
        assertEquals(true, (busy.single().step as PaywallStep.Choose).busy)
    }

    @Test
    fun `a payment the server could not record yet says it will be, and one not ours says so`() {
        answer = PlayVerification.Failed("Pas de connexion.", retry = true)
        val later = buy(open())
        assertEquals(PaywallMessage("Paiement reçu, mais pas encore enregistré : Pas de connexion. Il le sera à la prochaine vérification.", isError = true), later.message)

        answer = PlayVerification.Failed("Cet achat n'a pas pu être rattaché à votre entreprise. Contactez-nous.")
        assertEquals("Cet achat n'a pas pu être rattaché à votre entreprise. Contactez-nous.", buy(open()).message?.text)
    }

    @Test
    fun `the server asking for an e-mail sends the buyer to add one`() {
        answer = PlayVerification.EmailRequired
        assertEquals(PaywallStep.NeedsEmail, buy(open()).step)
    }

    @Test
    fun `a pending payment, a cancelled sheet and a failure each leave the plans`() {
        val state = open()
        play.buyResult = { BuyResult.Pending }
        val pending = buy(state)
        assertTrue(pending.step is PaywallStep.Choose)
        assertEquals(false, pending.message?.isError)
        assertTrue(verified.isEmpty())

        play.buyResult = { BuyResult.Cancelled }
        assertEquals(PaywallState(state.step), buy(state))

        play.buyResult = { BuyResult.Failed(BillingUnavailable.NETWORK) }
        assertEquals(PaywallMessage("Google Play ne répond pas. Vérifiez la connexion, puis réessayez.", isError = true), buy(state).message)
    }

    @Test
    fun `a subscription the Google account already holds is verified again on opening, and on buying it twice`() {
        play.ownedPurchases = listOf(OwnedPurchase("distroniq_solo", "tok-owned", purchased = true))
        val opened = open()
        assertEquals(listOf("tok-owned"), verified)
        assertEquals("tok-owned", (opened.step as PaywallStep.Choose).owned?.purchaseToken)
        assertNull("a quiet restore", opened.message)

        play.buyResult = { BuyResult.AlreadyOwned }
        buy(PaywallState((opened.step as PaywallStep.Choose).copy(owned = null)))
        assertEquals(listOf("tok-owned", "tok-owned"), verified)
    }

    @Test
    fun `a pending purchase held by the account is not sent to the server`() {
        play.ownedPurchases = listOf(OwnedPurchase("distroniq_solo", "tok-cash", purchased = false))
        assertNull((open().step as PaywallStep.Choose).owned)
        assertTrue(verified.isEmpty())
    }
}
