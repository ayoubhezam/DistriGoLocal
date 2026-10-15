package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.SessionStore
import com.distrigo.app.data.auth.SoftwareSessionCipher
import com.distrigo.app.data.auth.backend
import com.distrigo.app.data.auth.jsonResponse
import com.distrigo.app.data.auth.session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The app's check-ins (docs/license_l4_checkin.md §5): one at a time, retried only when retrying can help, and
 * logged. The process is fresh each time, as for the background job: nothing read before the coordinator runs.
 */
class LicenseCheckInTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val phone = FakePhone(Golden.issuedAt + 2 * SECOND)
    private val keys = FakeKeys().apply { hash = Golden.deviceHash }

    private val licenseDir by lazy { folder.newFolder("license") }
    private fun manager() = LicenseStore(licenseDir, HmacSeal()).let { store ->
        LicenseManager(store, LicenseVerifier(Golden.trusted), TrustedClock(store, phone), keys::publicKeyHash, { Golden.installationId }, Golden.PACKAGE)
    }
    private val license by lazy { manager() }
    private val auth by lazy {
        val sessions = SessionStore(folder.newFolder("auth"), SoftwareSessionCipher()).apply { save(session()) }
        AuthRepository(AuthApi(OkHttpClient(), server.backend()), sessions)
    }
    private val client by lazy { LicenseClient(OkHttpClient(), server.backend(), auth, keys, license, TestDeviceFacts) }
    private val logDir by lazy { folder.newFolder("log") }
    private var now = 1_000L
    private val checkIns by lazy { LicenseCheckIn(auth, license, client, CheckInLog(logDir)) { now } }

    @After
    fun stop() = server.shutdown()

    private val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
    private fun nonceReply() = jsonResponse(200, """{"status":"ok","nonce":"$nonce"}""")
    private fun renewed() = jsonResponse(200, """{"status":"ok","license":"${Golden.token("late")}"}""")
    private fun answer(status: String) = jsonResponse(200, """{"status":"$status"}""")

    /** Activated in an earlier run of the app: the license is in the files, nothing is in memory. */
    private fun activated() {
        assertTrue(manager().install(Golden.token("valid")) is LicenseCheck.Valid)
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
    }

    private fun log() = CheckInLog(logDir).read()

    @Test
    fun `two check-ins asked at once make one, and both get its answer`() = runBlocking {
        activated()
        server.enqueue(nonceReply().setBodyDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(renewed())
        val background = async(Dispatchers.Default) { checkIns.run(CheckInSource.BACKGROUND) }
        val inFront = async(Dispatchers.Default) { checkIns.run(CheckInSource.FOREGROUND) }
        assertEquals(CheckIn.Done, background.await())
        assertEquals(CheckIn.Done, inFront.await())
        assertEquals(2, server.requestCount)
        assertEquals(8L, (license.state.value as LicenseState.Judged).license.seq)

        // Once it is over, the next request makes a new one
        server.enqueue(nonceReply())
        server.enqueue(answer("subscription_inactive"))
        assertEquals(CheckIn.Suspended, checkIns.run(CheckInSource.FOREGROUND))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `only a failure of the network or the server is worth retrying`() = runBlocking {
        activated()
        server.enqueue(jsonResponse(503, "{}"))
        assertEquals(CheckIn.Failed(FunctionCalls.SERVER_MESSAGE, retry = true), checkIns.run(CheckInSource.BACKGROUND))

        // The nonce expired between the two calls: a new one will do
        server.enqueue(nonceReply())
        server.enqueue(answer("bad_nonce"))
        assertEquals(CheckIn.Failed(FunctionCalls.SERVER_MESSAGE, retry = true), checkIns.run(CheckInSource.BACKGROUND))

        // The server doesn't know this phone's key: no retry, and the log keeps why
        server.enqueue(nonceReply())
        server.enqueue(answer("bad_signature"))
        assertEquals(
            CheckIn.Failed("Ce téléphone n'a pas pu prouver son identité. Contactez DistriGo."),
            checkIns.run(CheckInSource.BACKGROUND),
        )

        server.shutdown()
        assertEquals(CheckIn.Failed(AuthError.NETWORK.message, retry = true), checkIns.run(CheckInSource.BACKGROUND))
        assertEquals("nothing changed on the phone", 7L, license.state.value.heldLicense?.seq)
    }

    @Test
    fun `signed out or without a license, the server is not called`() = runBlocking {
        activated()
        auth.restore()
        auth.forget()
        assertEquals(CheckIn.Failed(AuthError.SESSION_EXPIRED.message), checkIns.run(CheckInSource.BACKGROUND))

        license.clear()
        assertEquals(CheckIn.NotActivated, checkIns.run(CheckInSource.BACKGROUND))
        assertEquals(0, server.requestCount)
        assertEquals("not_activated", log().lastOutcome)
    }

    @Test
    fun `the log keeps who asked, what came of it, and when the server last answered`() = runBlocking {
        activated()
        server.enqueue(nonceReply())
        server.enqueue(renewed())
        checkIns.run(CheckInSource.FOREGROUND)
        assertEquals(CheckInLog.Entry(lastAttemptAt = 1_000, lastSource = CheckInSource.FOREGROUND, lastOutcome = "done", lastAnswerAt = 1_000), log())

        now = 2_000
        server.enqueue(jsonResponse(503, "{}"))
        checkIns.run(CheckInSource.BACKGROUND)
        assertEquals(
            CheckInLog.Entry(
                lastAttemptAt = 2_000,
                lastSource = CheckInSource.BACKGROUND,
                lastOutcome = "retry",
                lastError = FunctionCalls.SERVER_MESSAGE,
                lastAnswerAt = 1_000,
                lastBackgroundAttemptAt = 2_000,
            ),
            log(),
        )

        now = 3_000
        server.enqueue(nonceReply())
        server.enqueue(answer("subscription_inactive"))
        checkIns.run(CheckInSource.BACKGROUND)
        assertEquals(
            CheckInLog.Entry(
                lastAttemptAt = 3_000,
                lastSource = CheckInSource.BACKGROUND,
                lastOutcome = "suspended",
                lastAnswerAt = 3_000,
                lastBackgroundAttemptAt = 3_000,
                lastBackgroundAnswerAt = 3_000,
            ),
            log(),
        )
    }

    @Test
    fun `an unreadable log reads as empty and is replaced by the next check-in`() = runBlocking {
        java.io.File(logDir, "check_in.json").writeText("{not json")
        assertEquals(CheckInLog.Entry(), log())
        checkIns.run(CheckInSource.FOREGROUND)
        assertEquals("not_activated", log().lastOutcome)
    }
}
