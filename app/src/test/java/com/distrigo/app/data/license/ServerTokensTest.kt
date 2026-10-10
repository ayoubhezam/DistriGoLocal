package com.distrigo.app.data.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Licenses as the server issues them — claims from the SQL, signature from the Edge Functions' WebCrypto — for
 * a real Galaxy M34, verified by the phone's own code. Written by supabase/functions/tests/service.test.ts:
 * `DISTRIGO_EXPORT_GOLDEN=1 supabase/tests/local/run.sh`.
 */
class ServerTokensTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json: JsonObject by lazy {
        val stream = javaClass.getResourceAsStream("/license/server-golden.json") ?: error("server-golden.json not on the classpath")
        JsonParser().parse(stream.reader().readText()).asJsonObject
    }
    private val installationId get() = json.get("installation_id").asString
    private val deviceHash get() = json.get("device_hash").asString
    private fun token(name: String) = json.getAsJsonObject("tokens").get(name).asString

    private val verifier = LicenseVerifier(Golden.trusted)

    private fun binding(minSeq: Long = 0, deviceHash: String = this.deviceHash) =
        LicenseBinding(Golden.PACKAGE, installationId, deviceHash, minSeq)

    private fun verify(name: String, minSeq: Long = 0): License {
        val check = verifier.verify(token(name), binding(minSeq))
        assertTrue("$name: $check", check is LicenseCheck.Valid)
        return (check as LicenseCheck.Valid).license
    }

    @Test
    fun `the server's licenses verify on the phone they were issued to, in order`() {
        val first = verify("activation")
        assertEquals(1L, first.seq)
        assertEquals("trial", first.plan)
        assertEquals("test-a", first.keyId)
        assertEquals(deviceHash, first.deviceKeyHash)
        assertEquals(30, first.maxReboots)
        assertEquals(first.issuedAt + 14 * DAY, first.offlineUntil)
        assertEquals(first.validTo + 3 * DAY, first.graceUntil)

        val second = verify("refresh", minSeq = 2)
        assertEquals(2L, second.seq)
        assertEquals(LicenseCheck.Rejected(Rejection.REPLAYED), verifier.verify(token("activation"), binding(minSeq = 2)))
        assertEquals(LicenseCheck.Rejected(Rejection.WRONG_DEVICE), verifier.verify(token("refresh"), binding(deviceHash = Golden.deviceHash)))
    }

    @Test
    fun `the trial runs from the start of the day to the end of the 30th day after it, in Algiers`() {
        val license = verify("activation")
        val algiers = ZoneId.of("Africa/Algiers")
        val start = Instant.ofEpochMilli(license.validFrom).atZone(algiers)
        val end = Instant.ofEpochMilli(license.validTo).atZone(algiers)
        assertEquals(LocalTime.MIDNIGHT, start.toLocalTime())
        assertEquals(start.toLocalDate().plusDays(30), end.toLocalDate())
        assertEquals(LocalTime.of(23, 59, 59), end.toLocalTime())
    }

    @Test
    fun `a phone given the server's license is active at once, and takes the next one`() {
        val issued = verify("activation").issuedAt
        val phone = FakePhone(issued + 2 * SECOND)
        val store = LicenseStore(folder.root, HmacSeal())
        val manager = LicenseManager(
            store, verifier, TrustedClock(store, phone), { deviceHash }, { installationId }, Golden.PACKAGE,
        )
        assertTrue(manager.install(token("activation")) is LicenseCheck.Valid)
        assertTrue(manager.state.value is LicenseState.Active)
        phone.pass(HOUR)
        assertTrue(manager.install(token("refresh")) is LicenseCheck.Valid)
        val state = manager.state.value as LicenseState.Active
        assertEquals(2L, state.license.seq)
    }
}
