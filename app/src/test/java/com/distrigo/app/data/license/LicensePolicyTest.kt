package com.distrigo.app.data.license

import com.distrigo.app.data.license.LicensePolicy.Action
import com.distrigo.app.data.license.LicensePolicy.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** What each license state means for the screens: docs/license_l5_enforcement.md §1 (banners), §8.1 (the sheet), D4. */
class LicensePolicyTest {

    private val issued = Instant.parse("2026-12-01T08:00:00Z").toEpochMilli()
    private val validTo = Instant.parse("2026-12-31T22:00:00Z").toEpochMilli()
    private val graceUntil = Instant.parse("2027-01-03T22:00:00Z").toEpochMilli()
    private val offlineUntil = Instant.parse("2026-12-24T12:00:00Z").toEpochMilli()

    private fun license(plan: String = "solo") = License(
        keyId = "test-a", userId = "u", businessId = "b", deviceKeyHash = "d", installationId = "i",
        packageName = "com.distrigo.app", plan = plan, features = emptySet(),
        validFrom = issued, validTo = validTo, graceUntil = graceUntil,
        issuedAt = issued, offlineUntil = offlineUntil, maxReboots = 30, seq = 1, token = "",
    )

    private fun at(floor: Long, wall: Long = floor) = TrustedTime.Known(
        wall, AnchorState(floor = floor, elapsed = 0, boot = 1, reboots = 0, anchoredAt = issued, maxSeq = 1),
    )

    private fun policy(state: LicenseState) = LicensePolicy.of(state, ZoneOffset.UTC)

    @Test
    fun `active records and says nothing`() {
        val active = policy(LicenseState.Active(license(), at(issued)))
        assertTrue(active.canRecord)
        assertNull(active.banner)
        assertNull(active.block)
    }

    @Test
    fun `a phone date a little behind warns, and recording goes on`() {
        val behind = policy(LicenseState.Active(license(), at(issued + HOUR, wall = issued)))
        assertTrue(behind.canRecord)
        assertEquals(Tone.WARNING, behind.banner!!.tone)
        assertEquals(Action.DATE_SETTINGS, behind.banner!!.action)
    }

    @Test
    fun `the warnings record, and close for the day`() {
        val soon = policy(LicenseState.RenewSoon(license(), at(validTo - DAY)))
        assertTrue(soon.canRecord)
        assertEquals("Votre abonnement expire le 31/12/2026.", soon.banner!!.text)
        assertEquals(Tone.WARNING, soon.banner!!.tone)
        assertEquals(Action.SUBSCRIPTION, soon.banner!!.action)
        assertTrue(soon.banner!!.dismissible)

        val checkIn = policy(LicenseState.CheckInSoon(license(), at(offlineUntil - DAY)))
        assertTrue(checkIn.canRecord)
        assertEquals("Connectez-vous à Internet avant le 24/12 pour continuer à enregistrer.", checkIn.banner!!.text)
        assertNull(checkIn.banner!!.action)
        assertTrue(checkIn.banner!!.dismissible)
    }

    @Test
    fun `grace records under a red banner that stays`() {
        val grace = policy(LicenseState.Grace(license(), at(validTo + HOUR)))
        assertTrue(grace.canRecord)
        assertEquals("Abonnement expiré le 31/12. Période de grâce jusqu'au 03/01.", grace.banner!!.text)
        assertEquals(Tone.READ_ONLY, grace.banner!!.tone)
        assertFalse(grace.banner!!.dismissible)
        assertNull(grace.block)
    }

    @Test
    fun `expired is read-only, and the sheet offers to reactivate`() {
        val expired = policy(LicenseState.Expired(license(), at(graceUntil + HOUR)))
        assertFalse(expired.canRecord)
        assertEquals("Abonnement expiré — consultation seule. Sauvegarde et export restent disponibles.", expired.banner!!.text)
        assertFalse(expired.banner!!.dismissible)
        assertEquals(Action.SUBSCRIPTION, expired.block!!.action)
        assertEquals("Réactiver mon abonnement", expired.block!!.actionLabel)
    }

    @Test
    fun `expired by the phone's date alone asks to check the date or connect`() {
        // The floor the phone can prove is before the end of grace; only its date is past it
        val unsure = policy(LicenseState.Expired(license(), at(validTo - DAY, wall = graceUntil + DAY)))
        assertFalse(unsure.canRecord)
        assertEquals(Action.RETRY, unsure.block!!.action)
    }

    @Test
    fun `every state that blocks says why, with its button`() {
        val expected = mapOf(
            LicenseState.CheckInRequired(license(), CheckInReason.OFFLINE_TOO_LONG) to Action.RETRY,
            LicenseState.ClockBehind(license(), at(issued + 2 * DAY, wall = issued)) to Action.DATE_SETTINGS,
            LicenseState.Revoked to Action.ACCOUNT,
            LicenseState.Suspended(license()) to Action.SUBSCRIPTION,
            LicenseState.OtherBusiness(license(), "other") to Action.ACCOUNT,
            LicenseState.NoLicense() to Action.ACCOUNT,
            LicenseState.NotYetValid(license(), at(issued - DAY)) to Action.ACCOUNT,
        )
        for ((state, action) in expected) {
            val policy = policy(state)
            assertFalse("$state", policy.canRecord)
            assertEquals("$state", Tone.READ_ONLY, policy.banner!!.tone)
            assertFalse("$state", policy.banner!!.dismissible)
            assertEquals("$state", action, policy.block!!.action)
            assertTrue("$state", policy.block!!.reason.isNotBlank())
        }
    }

    @Test
    fun `a date set back more than a day gets its own card, with the date settings`() {
        val clock = policy(LicenseState.ClockBehind(license(), at(issued + 2 * DAY, wall = issued)))
        assertFalse(clock.canRecord)
        val card = clock.banner!!
        assertTrue(card.card)
        assertEquals("La date du téléphone est en retard", card.text)
        assertEquals("Activez « Date et heure automatiques », puis revenez.", card.detail)
        assertEquals(Action.DATE_SETTINGS, card.action)
        assertEquals("Régler la date", clock.block!!.actionLabel)
    }

    @Test
    fun `before the license is read, nothing is said and no sheet opens`() {
        val unchecked = policy(LicenseState.Unchecked)
        assertFalse(unchecked.canRecord)
        assertNull(unchecked.banner)
        assertNull(unchecked.block)
    }

    @Test
    fun `the tournée closes and the truck unloads whatever the license (D4)`() {
        for (state in listOf(LicenseState.Revoked, LicenseState.Expired(license(), at(graceUntil + HOUR)), LicenseState.Active(license(), at(issued)))) {
            val policy = policy(state)
            assertTrue(policy.canCloseTournee)
            assertTrue(policy.canUnloadTruck)
        }
        assertNotNull(policy(LicenseState.Revoked).block)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
