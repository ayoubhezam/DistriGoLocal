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
import org.junit.Assert.assertFalse
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

    @Test
    fun `an account for an activation code signs up with neither e-mail nor password, and knows it is anonymous`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"access_token":"access-1","token_type":"bearer","expires_in":3600,"expires_at":4102444800,
            "refresh_token":"refresh-1","user":{"id":"0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b","email":"","is_anonymous":true,
            "user_metadata":{},"app_metadata":{}}}"""))
        val outcome = api.signInAnonymously() as AuthOutcome.SignedIn
        val request = server.takeRequest()
        assertEquals("/auth/v1/signup", request.path)
        val body = request.json
        assertFalse(body.has("email"))
        assertFalse(body.has("password"))
        assertTrue(outcome.session.isAnonymous)
        assertNull(outcome.session.email)
        assertNull(outcome.session.initials)
        assertFalse(session().isAnonymous)

        server.enqueue(jsonResponse(422, """{"code":422,"error_code":"anonymous_provider_disabled","msg":"Anonymous sign-ins are disabled"}"""))
        assertEquals(AuthOutcome.Failed(AuthError.ANONYMOUS_DISABLED), api.signInAnonymously())
    }

    // ── « Ajouter une adresse e-mail » ──

    @Test
    fun `an e-mail and a password go together, as a PUT with the account's token`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"id":"u","email":"b@example.com","is_anonymous":false}"""))
        assertEquals(EmailUpdate.Done, api.updateUser("access-0", "b@example.com", "s3cret-pass"))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/auth/v1/user", request.path)
        assertEquals("Bearer access-0", request.getHeader("Authorization"))
        val body = request.json
        assertEquals("b@example.com", body.get("email").asString)
        assertEquals("s3cret-pass", body.get("password").asString)

        // E-mail confirmation turned on: the address waits in new_email
        server.enqueue(jsonResponse(200, """{"id":"u","email":"","new_email":"b@example.com","is_anonymous":true}"""))
        assertEquals(EmailUpdate.ConfirmByLink("b@example.com"), api.updateUser("access-0", "b@example.com", "s3cret-pass"))

        server.enqueue(jsonResponse(422, """{"code":422,"error_code":"email_exists","msg":"A user with this email address has already been registered"}"""))
        assertEquals(EmailUpdate.Failed(AuthError.EMAIL_TAKEN), api.updateUser("access-0", "b@example.com", "s3cret-pass"))

        server.enqueue(jsonResponse(422, """{"code":422,"error_code":"weak_password","msg":"Password should be at least 8 characters."}"""))
        assertEquals(EmailUpdate.Failed(AuthError.WEAK_PASSWORD), api.updateUser("access-0", "b@example.com", "short"))
    }

    @Test
    fun `a code account given an e-mail is refreshed into an e-mail account, same id`() = runBlocking {
        val anonymous = session().copy(email = null, displayName = null, provider = AuthSession.ANONYMOUS)
        val repository = repository(anonymous)
        server.enqueue(jsonResponse(200, """{"id":"0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b","email":"b@example.com","is_anonymous":false}"""))
        // Supabase may keep "anonymous" as the provider the account began with
        server.enqueue(jsonResponse(200, sessionJson(access = "access-2", refresh = "refresh-2", name = null, provider = AuthSession.ANONYMOUS)))

        assertEquals(EmailUpdate.Done, repository.addEmail(" b@example.com ", "s3cret-pass"))
        assertEquals("b@example.com", server.takeRequest().json.get("email").asString)
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)

        val now = (repository.state.value as AccountState.SignedIn).session
        assertFalse(now.isAnonymous)
        assertEquals("email", now.provider)
        assertEquals(anonymous.userId, now.userId)
        assertEquals("access-2", now.accessToken)
        assertEquals(now, SessionStore(folder.root, SoftwareSessionCipher()).load())
    }

    @Test
    fun `a refused e-mail leaves the code account as it was`() = runBlocking {
        val anonymous = session().copy(email = null, displayName = null, provider = AuthSession.ANONYMOUS)
        val repository = repository(anonymous)
        server.enqueue(jsonResponse(422, """{"code":422,"error_code":"email_exists","msg":"taken"}"""))
        assertEquals(EmailUpdate.Failed(AuthError.EMAIL_TAKEN), repository.addEmail("b@example.com", "s3cret-pass"))
        assertEquals(1, server.requestCount)
        assertEquals(anonymous, (repository.state.value as AccountState.SignedIn).session)
    }

    // ── ActivationCodeStore ──

    @Test
    fun `the activation code is encrypted at rest, with its business, and forgotten when cleared`() {
        val store = ActivationCodeStore(folder.root, SoftwareSessionCipher())
        assertNull(store.load())
        store.save("V1A9Y8XWJFC3", "business-1")
        assertEquals(ActivationCodeStore.Saved("V1A9Y8XWJFC3", "business-1"), store.saved.value)
        val bytes = File(folder.root, "activation_code.bin").readBytes()
        assertFalse("the code is not in the clear", String(bytes, Charsets.ISO_8859_1).contains("V1A9"))
        assertFalse("nor in a log line", store.saved.value.toString().contains("V1A9"))

        val restarted = ActivationCodeStore(folder.root, SoftwareSessionCipher())
        assertEquals(ActivationCodeStore.Saved("V1A9Y8XWJFC3", "business-1"), restarted.load())
        assertNull("another key reads nothing", ActivationCodeStore(folder.root, SoftwareSessionCipher(seed = 2)).load())

        restarted.clear()
        assertNull(restarted.saved.value)
        assertNull(ActivationCodeStore(folder.root, SoftwareSessionCipher()).load())
    }

    @Test
    fun `the code and the session live side by side`() {
        SessionStore(folder.root, SoftwareSessionCipher()).save(session())
        ActivationCodeStore(folder.root, SoftwareSessionCipher()).save("V1A9Y8XWJFC3", "business-1")
        SessionStore(folder.root, SoftwareSessionCipher()).clear()
        assertEquals("V1A9Y8XWJFC3", ActivationCodeStore(folder.root, SoftwareSessionCipher()).load()?.code)
    }

    // ── The owner's admin flag (C4) ──

    @Test
    fun `the owner's admin flag comes from app_metadata, the boolean true only, and is kept at rest`() {
        fun answer(flag: String) = com.google.gson.JsonParser().parse(
            """{"access_token":"a","refresh_token":"r","expires_in":3600,
                "user":{"id":"u","email":"o@example.com","app_metadata":{"provider":"email"$flag}}}"""
        ).asJsonObject
        val owner = AuthSession.fromAuthResponse(answer(""","distrigo_admin":true"""), 0)!!
        assertTrue(owner.isAdmin)
        assertFalse(AuthSession.fromAuthResponse(answer(""","distrigo_admin":"true""""), 0)!!.isAdmin)
        assertFalse(AuthSession.fromAuthResponse(answer(""), 0)!!.isAdmin)
        assertFalse(session().isAdmin)

        SessionStore(folder.root, SoftwareSessionCipher()).save(owner)
        assertTrue(SessionStore(folder.root, SoftwareSessionCipher()).load()!!.isAdmin)
    }

    @Test
    fun `a forced refresh brings what changed on the server, such as the admin flag`() = runBlocking {
        val repository = repository(session())
        server.enqueue(jsonResponse(200, """{"access_token":"access-2","token_type":"bearer","expires_in":3600,"expires_at":4102444800,
            "refresh_token":"refresh-2","user":{"id":"0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b","email":"a@example.com",
            "user_metadata":{"full_name":"Ayoub Test"},"app_metadata":{"provider":"email","distrigo_admin":true}}}"""))
        repository.refreshSession()
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertTrue((repository.state.value as AccountState.SignedIn).session.isAdmin)
    }
}
