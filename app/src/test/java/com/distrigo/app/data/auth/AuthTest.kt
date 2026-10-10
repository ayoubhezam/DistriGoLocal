package com.distrigo.app.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.io.File

/** Supabase Auth from the phone's side: the requests, the answers, the session at rest. */
class AuthTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val api = AuthApi(OkHttpClient(), server.backend(), clock = { 1_000_000L })

    @After
    fun stop() = server.shutdown()

    // ── AuthApi ──

    @Test
    fun `a sign-in posts the password grant with the project's key, and reads the session`() = runBlocking {
        server.enqueue(jsonResponse(200, sessionJson()))
        val outcome = api.signInWithPassword("a@example.com", "s3cret-pass") as AuthOutcome.SignedIn
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals("sb_publishable_test", request.getHeader("apikey"))
        val body = request.json
        assertEquals("a@example.com", body.get("email").asString)
        assertEquals("s3cret-pass", body.get("password").asString)
        val session = outcome.session
        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(4_102_444_800_000, session.expiresAt)
        assertEquals("Ayoub Test", session.displayName)
        assertEquals("AT", session.initials)
        assertEquals("email", session.provider)
    }

    @Test
    fun `a sign-up sends the name, and waits for the e-mail when Supabase gives no session`() = runBlocking {
        server.enqueue(jsonResponse(200, sessionJson()))
        assertTrue(api.signUp("a@example.com", "s3cret-pass", "Ayoub Test") is AuthOutcome.SignedIn)
        assertEquals("Ayoub Test", server.takeRequest().json.getAsJsonObject("data").get("full_name").asString)

        server.enqueue(jsonResponse(200, """{"id":"u","email":"a@example.com","confirmation_sent_at":"2026-10-10T10:00:00Z"}"""))
        assertEquals(AuthOutcome.ConfirmEmail("a@example.com"), api.signUp("a@example.com", "s3cret-pass", "Ayoub Test"))
    }

    @Test
    fun `Google's id token goes with its raw nonce`() = runBlocking {
        server.enqueue(jsonResponse(200, sessionJson(provider = "google")))
        val outcome = api.signInWithGoogle("google-id-token", "raw-nonce") as AuthOutcome.SignedIn
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=id_token", request.path)
        val body = request.json
        assertEquals("google", body.get("provider").asString)
        assertEquals("google-id-token", body.get("id_token").asString)
        assertEquals("raw-nonce", body.get("nonce").asString)
        assertEquals("google", outcome.session.provider)
    }

    @Test
    fun `each refusal from Supabase reads as its own message`() = runBlocking {
        val cases = listOf(
            jsonResponse(400, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""") to AuthError.INVALID_CREDENTIALS,
            jsonResponse(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""") to AuthError.INVALID_CREDENTIALS,
            jsonResponse(422, """{"code":422,"error_code":"user_already_exists","msg":"User already registered"}""") to AuthError.EMAIL_TAKEN,
            jsonResponse(422, """{"code":422,"error_code":"weak_password","msg":"Password should be at least 8 characters"}""") to AuthError.WEAK_PASSWORD,
            jsonResponse(400, """{"code":400,"error_code":"email_address_invalid","msg":"invalid"}""") to AuthError.INVALID_EMAIL,
            jsonResponse(400, """{"code":400,"error_code":"email_not_confirmed","msg":"Email not confirmed"}""") to AuthError.EMAIL_NOT_CONFIRMED,
            jsonResponse(400, """{"code":400,"error_code":"provider_disabled","msg":"Unsupported provider"}""") to AuthError.PROVIDER_DISABLED,
            jsonResponse(429, """{"code":429,"error_code":"over_request_rate_limit"}""") to AuthError.RATE_LIMITED,
            jsonResponse(429, "") to AuthError.RATE_LIMITED,
            jsonResponse(400, """{"code":400,"error_code":"refresh_token_not_found"}""") to AuthError.SESSION_EXPIRED,
            jsonResponse(502, "<html>bad gateway</html>") to AuthError.SERVER,
        )
        for ((response, expected) in cases) {
            server.enqueue(response)
            assertEquals(expected.name, AuthOutcome.Failed(expected), api.signInWithPassword("a@example.com", "x"))
        }
    }

    @Test
    fun `no server at all is a network error`() = runBlocking {
        val offline = AuthApi(OkHttpClient(), Backend("http://127.0.0.1:1", "k"))
        assertEquals(AuthOutcome.Failed(AuthError.NETWORK), offline.signInWithPassword("a@example.com", "x"))
    }

    // ── SessionStore ──

    @Test
    fun `the session is encrypted at rest and read back after a restart`() {
        val store = SessionStore(folder.root, SoftwareSessionCipher())
        assertNull(store.load())
        store.save(session())
        val bytes = File(folder.root, "session.bin").readBytes()
        assertTrue("the refresh token is not in the clear", !String(bytes, Charsets.ISO_8859_1).contains("refresh-0"))
        assertEquals(session(), SessionStore(folder.root, SoftwareSessionCipher()).load())
    }

    @Test
    fun `an altered file, or another key, reads as no session`() {
        SessionStore(folder.root, SoftwareSessionCipher()).save(session())
        assertNull(SessionStore(folder.root, SoftwareSessionCipher(seed = 2)).load())
        val file = File(folder.root, "session.bin")
        file.writeBytes(file.readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })
        assertNull(SessionStore(folder.root, SoftwareSessionCipher()).load())
    }

    // ── AuthRepository ──

    private fun repository(stored: AuthSession?, now: Long = 1_000_000L): AuthRepository {
        val store = SessionStore(folder.root, SoftwareSessionCipher())
        stored?.let(store::save)
        return AuthRepository(AuthApi(OkHttpClient(), server.backend(), clock = { now }), store, clock = { now }).also { it.restore() }
    }

    @Test
    fun `a fresh access token is used as is`() = runBlocking {
        val repository = repository(session())
        assertEquals(AccessToken.Granted("access-0"), repository.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an access token about to expire is renewed once, however many ask, and the new refresh token kept`() = runBlocking {
        server.enqueue(jsonResponse(200, sessionJson(access = "access-2", refresh = "refresh-2")))
        val repository = repository(session(expiresAt = 1_000_000L + 30_000))
        val tokens = List(5) { async { repository.accessToken() } }.awaitAll()
        assertTrue(tokens.all { it == AccessToken.Granted("access-2") })
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals("refresh-0", request.json.get("refresh_token").asString)
        assertEquals("refresh-2", SessionStore(folder.root, SoftwareSessionCipher()).load()?.refreshToken)
    }

    @Test
    fun `a session the server no longer knows signs the phone out, but no network does not`() = runBlocking {
        server.enqueue(jsonResponse(400, """{"error_code":"refresh_token_not_found"}"""))
        val ended = repository(session(expiresAt = 0))
        assertEquals(AccessToken.Denied(AuthError.SESSION_EXPIRED), ended.accessToken())
        assertEquals(AccountState.SignedOut, ended.state.value)
        assertNull(SessionStore(folder.root, SoftwareSessionCipher()).load())

        val store = SessionStore(folder.root, SoftwareSessionCipher()).apply { save(session(expiresAt = 0)) }
        val offline = AuthRepository(AuthApi(OkHttpClient(), Backend("http://127.0.0.1:1", "k")), store).also { it.restore() }
        assertEquals(AccessToken.Denied(AuthError.NETWORK), offline.accessToken())
        assertTrue(offline.state.value is AccountState.SignedIn)
    }

    @Test
    fun `signing in keeps the session, signing out forgets it even if the server cannot be told`() = runBlocking {
        val repository = repository(null)
        assertEquals(AccountState.SignedOut, repository.state.value)
        server.enqueue(jsonResponse(200, sessionJson()))
        repository.signIn(" a@example.com ", "s3cret-pass")
        assertEquals("a@example.com", server.takeRequest().json.get("email").asString)
        assertTrue(repository.state.value is AccountState.SignedIn)
        assertEquals("access-1", SessionStore(folder.root, SoftwareSessionCipher()).load()?.accessToken)

        server.enqueue(jsonResponse(500, ""))
        repository.signOut()
        assertEquals(AccountState.SignedOut, repository.state.value)
        assertNull(SessionStore(folder.root, SoftwareSessionCipher()).load())
    }
}
