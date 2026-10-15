package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.SessionStore
import com.distrigo.app.data.auth.SoftwareSessionCipher
import com.distrigo.app.data.auth.backend
import com.distrigo.app.data.auth.json
import com.distrigo.app.data.auth.jsonResponse
import com.distrigo.app.data.auth.session
import com.distrigo.app.data.auth.sessionJson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.Base64

/** Activation and check-in from the phone's side, against a server that answers as license-* do (docs §6.4). */
class LicenseClientTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val phone = FakePhone(Golden.issuedAt + 2 * SECOND)

    private val keys = FakeKeys()
    private val facts = TestDeviceFacts

    // Lazy: the temporary folder exists only once the rule has started.
    private val store by lazy { LicenseStore(folder.newFolder("license"), HmacSeal()) }
    private val license by lazy {
        LicenseManager(
            store, LicenseVerifier(Golden.trusted), TrustedClock(store, phone), keys::publicKeyHash,
            { Golden.installationId }, Golden.PACKAGE,
        )
    }
    private val auth by lazy {
        val sessions = SessionStore(folder.newFolder("auth"), SoftwareSessionCipher()).apply { save(session()) }
        AuthRepository(AuthApi(OkHttpClient(), server.backend()), sessions).also { it.restore() }
    }
    private val client by lazy { LicenseClient(OkHttpClient(), server.backend(), auth, keys, license, facts) }

    @After
    fun stop() = server.shutdown()

    private val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
    private fun nonceReply() = jsonResponse(200, """{"status":"ok","nonce":"$nonce"}""")
    private fun activated(token: String = Golden.token("valid")) =
        jsonResponse(200, """{"status":"activated","license":"$token","device_id":"d-1","business_id":"b","trial":true,"tier":"normal","reasons":[]}""")

    @Test
    fun `activation makes a key attested with the server's nonce, asks the business's name, then installs the license`() = runBlocking {
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"no_business"}"""))
        val asked = client.startActivation() as ActivationStep.NeedsBusinessName

        assertEquals("/functions/v1/license-nonce", server.takeRequest().path)
        val first = server.takeRequest()
        assertEquals("/functions/v1/license-activate", first.path)
        assertEquals("Bearer access-0", first.getHeader("Authorization"))
        val body = first.json
        assertEquals(nonce, body.get("nonce").asString)
        assertEquals(Golden.installationId, body.get("installation_id").asString)
        assertEquals("AQID", body.getAsJsonArray("certificate_chain")[0].asString)
        assertEquals("CQk=", body.get("signature").asString)
        assertEquals("android-1", body.get("android_id").asString)
        assertEquals("Samsung SM-M346B", body.get("model").asString)
        assertTrue(keys.challenge!!.contentEquals(ByteArray(32) { it.toByte() }))
        assertEquals("distrigo-activate\n$nonce\n${Golden.installationId}", keys.signed.single())

        server.enqueue(activated())
        val done = client.continueActivation(asked.request, businessName = "Distribution M34")
        assertEquals(ActivationStep.Activated(trial = true, reduced = false), done)
        val second = server.takeRequest().json
        assertEquals("Distribution M34", second.get("business_name").asString)
        assertEquals(nonce, second.get("nonce").asString)
        assertTrue(license.state.value is LicenseState.Active)
        assertEquals("d-1", license.deviceId())
    }

    @Test
    fun `a taken seat names the phones holding it, and yes transfers it here`() = runBlocking {
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"seat_taken","devices":[{"id":"d-9","model":"Xiaomi Redmi Note","last_seen_at":"2026-10-10T08:00:00.123+00:00"}]}"""))
        val taken = client.startActivation() as ActivationStep.SeatTaken
        assertEquals(listOf(SeatHolder("d-9", "Xiaomi Redmi Note", Instant.parse("2026-10-10T08:00:00.123Z"))), taken.holders)

        server.enqueue(activated())
        assertTrue(client.continueActivation(taken.request, transfer = true) is ActivationStep.Activated)
        server.takeRequest()
        server.takeRequest()
        assertTrue(server.takeRequest().json.get("transfer").asBoolean)
    }

    @Test
    fun `the server's other answers each say what to do`() = runBlocking {
        val cases = listOf(
            """{"status":"transfer_limit","next_at":"2026-11-09T08:00:00+00:00"}""" to ActivationStep.TransferLimit(Instant.parse("2026-11-09T08:00:00Z")),
            """{"status":"refused","reason":"repackaged"}""" to ActivationStep.Failed("Cette copie de DistriGo n'est pas l'application officielle."),
            """{"status":"bad_nonce"}""" to ActivationStep.Failed("Le délai est dépassé. Recommencez l'activation."),
        )
        for ((answer, expected) in cases) {
            server.enqueue(nonceReply())
            server.enqueue(jsonResponse(200, answer))
            assertEquals(answer, expected, client.startActivation())
        }
    }

    @Test
    fun `a phone holding a license is never activated again, a new key would break it`() = runBlocking {
        keys.hash = Golden.deviceHash
        assertTrue(license.install(Golden.token("valid")) is LicenseCheck.Valid)
        assertEquals(ActivationStep.AlreadyActivated, client.startActivation())
        assertEquals(0, server.requestCount)
        assertNull(keys.challenge)
    }

    @Test
    fun `a check-in proves the key and installs the next license, and revoked revokes the phone`() = runBlocking {
        keys.hash = Golden.deviceHash
        license.install(Golden.token("valid"))
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)

        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"ok","license":"${Golden.token("late")}"}"""))
        assertEquals(CheckIn.Done, client.checkIn())
        server.takeRequest()
        val body = server.takeRequest().json
        assertEquals(Golden.installationId, body.get("installation_id").asString)
        assertEquals("distrigo-refresh\n$nonce\n${Golden.installationId}", keys.signed.single())
        assertEquals(8L, (license.state.value as LicenseState.Judged).license.seq)

        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"revoked"}"""))
        assertEquals(CheckIn.Revoked, client.checkIn())
        assertEquals(LicenseState.Revoked, license.state.value)
        assertEquals(CheckIn.NotActivated, client.checkIn())
    }

    @Test
    fun `a suspended subscription keeps the license here, and the check-in after the reactivation heals it`() = runBlocking {
        keys.hash = Golden.deviceHash
        license.install(Golden.token("valid"))

        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"subscription_inactive"}"""))
        assertEquals(CheckIn.Suspended, client.checkIn())
        assertEquals(7L, (license.state.value as LicenseState.Suspended).license.seq)
        assertEquals("a suspended phone is not activated again", ActivationStep.AlreadyActivated, client.startActivation())

        // A code adds its days, but the license waits for the reactivation
        server.enqueue(jsonResponse(200, """{"status":"renewed","business_id":"b","valid_to":"2027-04-14","plan":"solo","days":182}"""))
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"subscription_inactive"}"""))
        assertEquals(
            Renewal.Failed("Le code a bien été ajouté, mais l'abonnement est suspendu. Contactez DistriGo."),
            client.renewWithCode("7KQ4-M2XP-9HTD"),
        )

        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"ok","license":"${Golden.token("late")}"}"""))
        assertEquals(CheckIn.Done, client.checkIn())
        assertEquals(8L, (license.state.value as LicenseState.Judged).license.seq)
        assertEquals(7, server.requestCount)
    }

    @Test
    fun `a refused access token is renewed once, and the call made again`() = runBlocking {
        server.enqueue(jsonResponse(401, """{"status":"unauthenticated"}"""))
        server.enqueue(jsonResponse(200, sessionJson(access = "access-2", refresh = "refresh-2")))
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"no_business"}"""))
        assertTrue(client.startActivation() is ActivationStep.NeedsBusinessName)
        assertEquals("Bearer access-0", server.takeRequest().getHeader("Authorization"))
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `releasing frees the seat on the server, then forgets the license, and deleting forgets the account too`() = runBlocking {
        server.enqueue(nonceReply())
        server.enqueue(activated())
        client.startActivation()
        server.takeRequest()
        server.takeRequest()

        server.enqueue(jsonResponse(200, """{"status":"released"}"""))
        assertNull(client.releaseThisDevice())
        assertEquals("d-1", server.takeRequest().json.get("device_id").asString)
        assertEquals(LicenseState.NoLicense(), license.state.value)
        assertNull(license.deviceId())

        server.enqueue(jsonResponse(200, """{"status":"deleted"}"""))
        assertNull(client.deleteAccount())
        assertEquals(AccountState.SignedOut, auth.state.value)
    }

    @Test
    fun `without a network nothing changes, and the user is told`() = runBlocking {
        server.shutdown()
        assertEquals(ActivationStep.Failed(AuthError.NETWORK.message), client.startActivation())
        assertNull(keys.challenge)
    }

    @Test
    fun `a code goes with the activation, the company follows when it names none, and the answer says what the code did`() = runBlocking {
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"code_needs_business"}"""))
        val asked = client.startActivation(ActivationDetails(activationCode = "7kq4 m2xp 9htd")) as ActivationStep.CodeNeedsBusiness
        server.takeRequest()
        assertEquals("7kq4 m2xp 9htd", server.takeRequest().json.get("activation_code").asString)

        server.enqueue(jsonResponse(200, """{"status":"activated","license":"${Golden.token("valid")}","device_id":"d-1","business_id":"b",
            "trial":false,"tier":"normal","reasons":[],"code":"created"}"""))
        val done = client.continueActivation(asked.request, businessName = "Épicerie Benali", contactPhone = "0661 23 45 67")
        assertEquals(ActivationStep.Activated(trial = false, reduced = false, code = "created"), done)
        val second = server.takeRequest().json
        assertEquals("7kq4 m2xp 9htd", second.get("activation_code").asString)
        assertEquals("Épicerie Benali", second.get("business_name").asString)
        assertEquals("0661 23 45 67", second.get("contact_phone").asString)
        assertTrue(license.state.value is LicenseState.Active)
    }

    @Test
    fun `a new account's business goes with its first activation`() = runBlocking {
        server.enqueue(nonceReply())
        server.enqueue(activated())
        client.startActivation(ActivationDetails(businessName = "Grossiste F7", contactPhone = "0550 11 22 33", activity = "boissons", salesReps = "2-5"))
        server.takeRequest()
        val body = server.takeRequest().json
        assertEquals("Grossiste F7", body.get("business_name").asString)
        assertEquals("0550 11 22 33", body.get("contact_phone").asString)
        assertEquals("boissons", body.get("business_activity").asString)
        assertEquals("2-5", body.get("business_sales_reps").asString)
        assertNull(body.get("activation_code"))
    }

    @Test
    fun `a refused code says why, and when to try again`() = runBlocking {
        val retry = "2026-10-14T21:38:00+00:00"
        val at = DateTimeFormatter.ofPattern("HH:mm").format(Instant.parse("2026-10-14T21:38:00Z").atZone(ZoneId.systemDefault()))
        val cases = listOf(
            """{"status":"bad_code"}""" to ActivationStep.Failed("Ce code n'existe pas ou n'est plus valable."),
            """{"status":"code_expired"}""" to ActivationStep.Failed("Ce code a expiré. Demandez-en un nouveau à votre revendeur."),
            """{"status":"code_used"}""" to ActivationStep.Failed("Ce code a déjà servi à un renouvellement."),
            """{"status":"too_many_attempts","retry_at":"$retry"}""" to ActivationStep.Failed("Trop d'essais. Réessayez après $at."),
        )
        for ((answer, expected) in cases) {
            server.enqueue(nonceReply())
            server.enqueue(jsonResponse(200, answer))
            assertEquals(answer, expected, client.startActivation(ActivationDetails(activationCode = "7KQ4M2XP9HTD")))
        }
    }

    @Test
    fun `a code renews the business, then a check-in brings the license it pays for`() = runBlocking {
        keys.hash = Golden.deviceHash
        license.install(Golden.token("valid"))
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)

        server.enqueue(jsonResponse(200, """{"status":"renewed","business_id":"b","valid_to":"2027-04-14","plan":"solo","days":182}"""))
        server.enqueue(nonceReply())
        server.enqueue(jsonResponse(200, """{"status":"ok","license":"${Golden.token("late")}"}"""))
        assertEquals(Renewal.Renewed, client.renewWithCode("7KQ4-M2XP-9HTD"))
        val redeem = server.takeRequest()
        assertEquals("/functions/v1/code-redeem", redeem.path)
        assertEquals("7KQ4-M2XP-9HTD", redeem.json.get("code").asString)
        assertEquals("/functions/v1/license-nonce", server.takeRequest().path)
        assertEquals("/functions/v1/license-refresh", server.takeRequest().path)
        assertEquals(8L, (license.state.value as LicenseState.Judged).license.seq)

        server.enqueue(jsonResponse(200, """{"status":"code_used"}"""))
        assertEquals(Renewal.Failed("Ce code a déjà été utilisé."), client.renewWithCode("7KQ4-M2XP-9HTD"))
    }

    // ── « Informations de l'entreprise » ──

    @Test
    fun `the business profile is read with an empty request, and changed field by field`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok","business_id":"b","name":"DISTRIGO","contact_phone":null,
            "activity":null,"sales_reps":null,"can_edit":true}"""))
        assertEquals(
            ProfileAnswer.Found(BusinessProfile("b", "DISTRIGO", contactPhone = null, activity = null, salesReps = null, canEdit = true)),
            client.businessProfile(),
        )
        val read = server.takeRequest()
        assertEquals("/functions/v1/business-profile", read.path)
        assertEquals(0, read.json.size())

        server.enqueue(jsonResponse(200, """{"status":"ok","business_id":"b","name":"DISTRIGO","contact_phone":null,
            "activity":"boissons","sales_reps":"2-5","can_edit":true}"""))
        val changed = client.businessProfile(mapOf("activity" to "boissons", "contact_phone" to null)) as ProfileAnswer.Found
        assertEquals("boissons", changed.profile.activity)
        assertEquals("2-5", changed.profile.salesReps)
        val body = server.takeRequest().json
        assertEquals("boissons", body.get("activity").asString)
        assertTrue("a cleared phone is sent as null", body.has("contact_phone") && body.get("contact_phone").isJsonNull)
    }

    @Test
    fun `the business profile's refusals read in French`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"no_business"}"""))
        assertEquals(ProfileAnswer.NoBusiness, client.businessProfile())
        server.enqueue(jsonResponse(200, """{"status":"not_allowed"}"""))
        assertEquals(
            ProfileAnswer.Failed("Seuls le gérant et les responsables peuvent modifier ces informations."),
            client.businessProfile(mapOf("name" to "X")),
        )
        server.enqueue(jsonResponse(400, """{"status":"bad_request","field":"name"}"""))
        assertTrue(client.businessProfile(mapOf("name" to "")) is ProfileAnswer.Failed)
        server.enqueue(jsonResponse(200, """{"status":"ok","business_id":"b"}"""))
        assertTrue("a profile without its name is not one", client.businessProfile() is ProfileAnswer.Failed)
    }

    @Test
    fun `the business profile is kept for the account it was read for`() {
        val store = BusinessProfileStore(folder.root)
        assertNull(store.load())
        val profile = BusinessProfile("b", "Épicerie Benali", "0661 23 45 67", "alimentation", "1", canEdit = true)
        store.save("u-1", profile)
        assertEquals(BusinessProfileStore.Saved("u-1", profile), BusinessProfileStore(folder.root).load())
        store.save("u-1", profile.copy(contactPhone = null, salesReps = null))
        assertEquals(profile.copy(contactPhone = null, salesReps = null), BusinessProfileStore(folder.root).load()?.profile)
        store.clear()
        assertNull(store.saved.value)
        assertNull(BusinessProfileStore(folder.root).load())
    }
}
