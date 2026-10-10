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
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.Base64

/** Activation and check-in from the phone's side, against a server that answers as license-* do (docs §6.4). */
class LicenseClientTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val phone = FakePhone(Golden.issuedAt + 2 * SECOND)

    /** The Keystore, played: the key it makes is the golden licenses' device. */
    private class FakeKeys : DeviceKeys {
        var hash: String? = null
        var challenge: ByteArray? = null
        val signed = mutableListOf<String>()
        override fun publicKeyHash() = hash
        override fun create(challenge: ByteArray): CreatedKey {
            this.challenge = challenge
            hash = Golden.deviceHash
            return CreatedKey(Golden.deviceHash, listOf(byteArrayOf(1, 2, 3)), strongBox = false, attested = true)
        }
        override fun sign(data: ByteArray): ByteArray {
            signed += String(data)
            return byteArrayOf(9, 9)
        }
    }

    private val keys = FakeKeys()
    private val facts = object : DeviceFacts {
        override val installationId = Golden.installationId
        override val androidId = "android-1"
        override val model = "Samsung SM-M346B"
        override val osVersion = "16"
        override val appVersion = "1.0"
    }

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
}
