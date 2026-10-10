package com.distrigo.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every golden token, verified: docs/license_architecture.md §4.2. */
class LicenseVerifierTest {

    private val verifier = LicenseVerifier(Golden.trusted)

    private fun verify(name: String, minSeq: Long = 0) = verifier.verify(Golden.token(name), Golden.binding(minSeq))

    private fun valid(name: String, minSeq: Long = 0): License {
        val check = verify(name, minSeq)
        assertTrue("$name: $check", check is LicenseCheck.Valid)
        return (check as LicenseCheck.Valid).license
    }

    /** What each refused golden token must be refused for. */
    private val refusals = mapOf(
        "two_parts" to Rejection.MALFORMED,
        "four_parts" to Rejection.MALFORMED,
        "bad_base64" to Rejection.MALFORMED,
        "header_not_json" to Rejection.MALFORMED,
        "alg_none" to Rejection.WRONG_ALGORITHM,
        "alg_hs256" to Rejection.WRONG_ALGORITHM,
        "wrong_typ" to Rejection.WRONG_TYPE,
        "no_typ" to Rejection.WRONG_TYPE,
        "no_kid" to Rejection.UNKNOWN_KEY,
        "unknown_kid" to Rejection.UNKNOWN_KEY,
        "kid_swapped" to Rejection.BAD_SIGNATURE,
        "tampered_payload" to Rejection.BAD_SIGNATURE,
        "der_signature" to Rejection.BAD_SIGNATURE,
        "truncated_signature" to Rejection.BAD_SIGNATURE,
        "payload_not_object" to Rejection.MALFORMED,
        "missing_claim" to Rejection.MALFORMED,
        "claim_wrong_type" to Rejection.MALFORMED,
        "wrong_issuer" to Rejection.WRONG_ISSUER,
        "other_package" to Rejection.WRONG_PACKAGE,
        "other_installation" to Rejection.WRONG_INSTALLATION,
        "other_device" to Rejection.WRONG_DEVICE,
    )

    private val accepted = setOf("valid", "valid_test_b", "late", "older_seq", "extra_claims")

    @Test
    fun `every golden token is covered by this test`() {
        assertEquals(Golden.tokenNames, refusals.keys + accepted)
    }

    @Test
    fun `each forged or foreign token is refused for its own reason`() {
        for ((name, why) in refusals) {
            assertEquals(name, LicenseCheck.Rejected(why), verify(name))
        }
    }

    @Test
    fun `a license signed by the server reads back its claims, times in milliseconds`() {
        val license = valid("valid")
        assertEquals("test-a", license.keyId)
        assertEquals("0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b", license.userId)
        assertEquals("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", license.businessId)
        assertEquals(Golden.deviceHash, license.deviceKeyHash)
        assertEquals(Golden.installationId, license.installationId)
        assertEquals("trial", license.plan)
        assertEquals(emptySet<String>(), license.features)
        assertEquals(java.time.Instant.parse("2026-10-10T08:00:00Z").toEpochMilli(), license.issuedAt)
        assertEquals(java.time.Instant.parse("2026-11-09T22:59:59Z").toEpochMilli(), license.validTo)
        assertEquals(Golden.validFrom, license.validFrom)
        assertEquals(Golden.graceUntil, license.graceUntil)
        assertEquals(license.issuedAt + 14 * DAY, license.offlineUntil)
        assertEquals(30, license.maxReboots)
        assertEquals(7L, license.seq)
        assertEquals(Golden.token("valid"), license.token)
    }

    @Test
    fun `either pinned key may sign`() {
        assertEquals("test-b", valid("valid_test_b").keyId)
    }

    @Test
    fun `a license checked in late in the subscription may stay offline until the end of the grace`() {
        val late = valid("late")
        assertEquals(8L, late.seq)
        assertEquals(late.graceUntil, late.offlineUntil)
    }

    @Test
    fun `claims a later server adds are ignored, so phones not yet updated keep working`() {
        assertEquals(setOf("rapports-pro"), valid("extra_claims").features)
    }

    @Test
    fun `a license older than one already accepted is refused`() {
        assertEquals(6L, valid("older_seq").seq)
        assertEquals(LicenseCheck.Rejected(Rejection.REPLAYED), verify("older_seq", minSeq = 7))
        assertEquals(7L, valid("valid", minSeq = 7).seq)
        assertEquals(LicenseCheck.Rejected(Rejection.REPLAYED), verify("valid", minSeq = 8))
    }

    @Test
    fun `a verifier that does not pin the key refuses the license`() {
        val onlyB = LicenseVerifier(Golden.trusted.filterKeys { it == "test-b" })
        assertEquals(LicenseCheck.Rejected(Rejection.UNKNOWN_KEY), onlyB.verify(Golden.token("valid"), Golden.binding()))
        assertEquals(
            LicenseCheck.Rejected(Rejection.UNKNOWN_KEY),
            LicenseVerifier(emptyMap()).verify(Golden.token("valid"), Golden.binding()),
        )
    }

    @Test
    fun `the device hash the server writes is the one the phone computes from its key`() {
        assertEquals(Golden.deviceHash, LicenseCrypto.keyHash(Golden.deviceSpki))
    }

    @Test
    fun `debug builds trust the test keys and other builds never do`() {
        val debug = LicenseKeys.trusted(debug = true)
        for ((kid, key) in Golden.trustedKeys) {
            assertTrue("debug builds pin $kid", debug[kid]?.encoded?.contentEquals(java.util.Base64.getDecoder().decode(key)) == true)
        }
        assertTrue(LicenseKeys.trusted(debug = false).keys.none { it in Golden.trustedKeys })
    }
}
