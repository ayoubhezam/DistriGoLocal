package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.SessionStore
import com.distrigo.app.data.auth.SoftwareSessionCipher
import com.distrigo.app.data.auth.backend
import com.distrigo.app.data.auth.json
import com.distrigo.app.data.auth.jsonResponse
import com.distrigo.app.data.auth.session
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/** The « Administration » screen's calls to codes-admin (docs/activation_codes.md, step C4). */
class CodesAdminClientTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }

    private val client by lazy {
        val sessions = SessionStore(folder.newFolder("auth"), SoftwareSessionCipher()).apply { save(session()) }
        val auth = AuthRepository(AuthApi(OkHttpClient(), server.backend()), sessions).also { it.restore() }
        CodesAdminClient(OkHttpClient(), server.backend(), auth)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `the batches are read with what became of their codes`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok","batches":[
            {"batch":"Revendeur Sétif","plan":"solo","days":182,"total":50,"unused":41,"keys":7,"renewals":1,"expired":0,
             "revoked":1,"created_on":"2026-10-14"}]}"""))
        val batches = (client.batches() as AdminAnswer.Done).value
        assertEquals(listOf(CodeBatch("Revendeur Sétif", "solo", 182, 50, 41, 7, 1, 0, 1, LocalDate.of(2026, 10, 14))), batches)
        val request = server.takeRequest()
        assertEquals("/functions/v1/codes-admin", request.path)
        assertEquals("batches", request.json.get("action").asString)
    }

    @Test
    fun `a batch is ordered with what the screen chose, and its codes come back once`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"created","batch":"Salon","plan":"solo","days":30,
            "codes":[{"code":"7KQ4-M2XP-9HTD","expires_on":"2027-10-14"},{"code":"V1A9-Y8XW-JFC3","expires_on":null}]}"""))
        val codes = (client.create(CodeOrder(count = 2, days = 30, batch = " Salon ", businessName = "  ", note = "stand")) as AdminAnswer.Done).value
        assertEquals(listOf(NewCode("7KQ4-M2XP-9HTD", LocalDate.of(2027, 10, 14)), NewCode("V1A9-Y8XW-JFC3", null)), codes)
        val body = server.takeRequest().json
        assertEquals("create", body.get("action").asString)
        assertEquals(2, body.get("count").asInt)
        assertEquals("solo", body.get("plan").asString)
        assertEquals(30, body.get("days").asInt)
        assertEquals("Salon", body.get("batch").asString)
        assertEquals("stand", body.get("note").asString)
        assertFalse("a blank company is not sent", body.has("business_name"))
        assertFalse("the default expiry is the server's", body.has("expires_on"))

        server.enqueue(jsonResponse(200, """{"status":"created","codes":[]}"""))
        client.create(CodeOrder(count = 1, days = 30, batch = "Sans fin", expiry = CodeExpiry.Never))
        val never = server.takeRequest().json
        assertTrue("never: expires_on sent as null", never.has("expires_on") && never.get("expires_on").isJsonNull)

        server.enqueue(jsonResponse(200, """{"status":"created","codes":[]}"""))
        client.create(CodeOrder(count = 1, days = 30, batch = "Daté", expiry = CodeExpiry.On(LocalDate.of(2027, 6, 30))))
        assertEquals("2027-06-30", server.takeRequest().json.get("expires_on").asString)
    }

    @Test
    fun `a batch is opened by its name, plan and days, with the business each used code went to`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok","batch":"Revendeur Sétif","plan":"solo","days":365,"total":5,"unused":3,
            "keys":1,"renewals":1,"expired":0,"revoked":0,"created_on":"2026-10-15","expires_on":"2026-11-14","business_name":null,
            "note":"payé en espèces","live_keys":1,"uses":[
              {"use":"renewal","business_name":"Dépôt Renouvelé","redeemed_at":"2026-10-15T10:00:00.5+00:00"},
              {"use":"key","business_name":null,"redeemed_at":"2026-10-15T09:00:00+00:00"}]}"""))
        val asked = CodeBatch("Revendeur Sétif", "solo", 365, 5, 3, 1, 1, 0, 0, LocalDate.of(2026, 10, 15))
        val details = (client.batch(asked) as AdminAnswer.Done).value!!
        assertEquals(asked, details.batch)
        assertEquals(LocalDate.of(2026, 11, 14), details.expiresOn)
        assertEquals("payé en espèces", details.note)
        assertEquals(1, details.liveKeys)
        assertEquals(
            listOf(
                BatchUse(key = false, businessName = "Dépôt Renouvelé", redeemedAt = java.time.Instant.parse("2026-10-15T10:00:00.5Z")),
                BatchUse(key = true, businessName = null, redeemedAt = java.time.Instant.parse("2026-10-15T09:00:00Z")),
            ),
            details.uses,
        )
        val body = server.takeRequest().json
        assertEquals("batch", body.get("action").asString)
        assertEquals("Revendeur Sétif", body.get("batch").asString)
        assertEquals("solo", body.get("plan").asString)
        assertEquals(365, body.get("days").asInt)

        server.enqueue(jsonResponse(200, """{"status":"unknown_batch"}"""))
        assertEquals(AdminAnswer.Done(null), client.batch(asked))
    }

    @Test
    fun `a batch's free codes are revoked, and its deletion is refused while a code opens a business`() = runBlocking {
        val batch = CodeBatch("Salon", "solo", 30, 2, 2, 0, 0, 0, 0, null)
        server.enqueue(jsonResponse(200, """{"status":"ok","revoked":2}"""))
        assertEquals(AdminAnswer.Done(2), client.revokeBatch(batch, reason = " Revendeur parti "))
        val revoke = server.takeRequest().json
        assertEquals("revoke_batch", revoke.get("action").asString)
        assertEquals("Revendeur parti", revoke.get("reason").asString)

        server.enqueue(jsonResponse(200, """{"status":"in_use","keys":3}"""))
        assertEquals(AdminAnswer.Done(BatchDeletion.InUse(3)), client.deleteBatch(batch))
        assertEquals("delete_batch", server.takeRequest().json.get("action").asString)
        server.enqueue(jsonResponse(200, """{"status":"deleted","codes":2}"""))
        assertEquals(AdminAnswer.Done(BatchDeletion.Deleted(2)), client.deleteBatch(batch))
        server.enqueue(jsonResponse(200, """{"status":"unknown_batch"}"""))
        assertEquals(AdminAnswer.Done(BatchDeletion.Unknown), client.deleteBatch(batch))
        server.enqueue(jsonResponse(200, """{"status":"not_allowed"}"""))
        assertEquals(AdminAnswer.NotAllowed, client.revokeBatch(batch, reason = null))
    }

    @Test
    fun `a code is checked, then revoked`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"key","batch":"Salon","plan":"solo","days":30,"expires_on":"2027-10-14",
            "business_id":"b","business_name":"Épicerie Benali","redeemed_at":"2026-10-14T10:09:53.123+00:00","revoked_at":null,"note":null}"""))
        val state = (client.status("7KQ4-M2XP-9HTD") as AdminAnswer.Done).value
        assertEquals("key", state.status)
        assertEquals("Épicerie Benali", state.businessName)
        assertEquals(LocalDate.of(2027, 10, 14), state.expiresOn)
        assertEquals(1_791_972_593_123L, state.redeemedAt?.toEpochMilli())
        val asked = server.takeRequest().json
        assertEquals("status", asked.get("action").asString)
        assertEquals("7KQ4-M2XP-9HTD", asked.get("code").asString)

        server.enqueue(jsonResponse(200, """{"status":"revoked"}"""))
        assertEquals(AdminAnswer.Done(true), client.revoke("7KQ4-M2XP-9HTD", "Perdu"))
        assertEquals("Perdu", server.takeRequest().json.get("reason").asString)
        server.enqueue(jsonResponse(200, """{"status":"unknown"}"""))
        assertEquals(AdminAnswer.Done(false), client.revoke("0000-0000-0000", null))
    }

    @Test
    fun `anyone but the owner is told so, and a strange answer is a failure`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"not_allowed"}"""))
        assertEquals(AdminAnswer.NotAllowed, client.batches())
        server.enqueue(jsonResponse(200, """{"status":"not_allowed"}"""))
        assertEquals(AdminAnswer.NotAllowed, client.create(CodeOrder(count = 1, days = 30, batch = "X")))
        server.enqueue(jsonResponse(200, """{"status":"surprise"}"""))
        assertTrue(client.batches() is AdminAnswer.Failed)
        server.enqueue(jsonResponse(400, """{"status":"bad_request","field":"batch"}"""))
        assertTrue(client.create(CodeOrder(count = 1, days = 30, batch = "")) is AdminAnswer.Failed)
    }

    // ── « Comptes clients » ──

    @Test
    fun `clients are searched, and read with their subscription`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok","businesses":[{"business_id":"b-1","name":"Épicerie Lynda",
            "contact_phone":"0699 81 72 63","created_on":"2026-10-14","plan":"solo","status":"suspended","valid_from":"2026-10-14",
            "valid_to":"2027-04-14","phones":1,"last_seen":"2026-10-15T08:30:00+00:00","owner_email":"lynda@example.com","by_code":false}]}"""))
        val found = (client.businesses(" lynda ") as AdminAnswer.Done).value.single()
        assertEquals("Épicerie Lynda", found.name)
        assertEquals("suspended", found.status)
        assertEquals(LocalDate.of(2027, 4, 14), found.validTo)
        assertEquals(1, found.phones)
        assertEquals("lynda@example.com", found.ownerEmail)
        assertFalse(found.byCode)
        val body = server.takeRequest().json
        assertEquals("businesses", body.get("action").asString)
        assertEquals("lynda", body.get("query").asString)

        server.enqueue(jsonResponse(200, """{"status":"ok","businesses":[]}"""))
        client.businesses("  ")
        assertFalse("an empty search asks for the latest", server.takeRequest().json.has("query"))
    }

    @Test
    fun `a client is suspended, reactivated and deleted, and the owner's own business is refused`() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok","subscription_status":"suspended"}"""))
        assertEquals(AdminAnswer.Done(ClientOutcome.DONE), client.setSuspended("b-1", suspended = true, note = "Impayé"))
        val suspend = server.takeRequest().json
        assertEquals("suspend", suspend.get("action").asString)
        assertEquals("b-1", suspend.get("business_id").asString)
        assertEquals("Impayé", suspend.get("note").asString)

        server.enqueue(jsonResponse(200, """{"status":"ok","subscription_status":"active"}"""))
        client.setSuspended("b-1", suspended = false, note = null)
        assertEquals("reactivate", server.takeRequest().json.get("action").asString)

        server.enqueue(jsonResponse(200, """{"status":"deleted"}"""))
        assertEquals(AdminAnswer.Done(ClientOutcome.DONE), client.deleteBusiness("b-1"))
        assertEquals("delete_business", server.takeRequest().json.get("action").asString)

        server.enqueue(jsonResponse(200, """{"status":"own_business"}"""))
        assertEquals(AdminAnswer.Done(ClientOutcome.OWN_BUSINESS), client.deleteBusiness("mine"))
        server.enqueue(jsonResponse(200, """{"status":"unknown_business"}"""))
        assertEquals(AdminAnswer.Done(ClientOutcome.UNKNOWN), client.setSuspended("gone", suspended = true, note = null))
    }
}
