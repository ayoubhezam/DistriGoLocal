package com.distrigo.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.reflect.KClass

/** What a license allows at each moment: docs/license_architecture.md §2.5, boundary by boundary. */
class LicenseRulesTest {

    private val issued = Instant.parse("2026-10-10T08:00:00Z").toEpochMilli()
    private val validTo = Instant.parse("2026-11-09T22:59:59Z").toEpochMilli()
    private val graceUntil = validTo + 3 * DAY

    /** A license checked in late enough that its offline window runs to the end of grace. */
    private val license = License(
        keyId = "test-a", userId = "u", businessId = "b", deviceKeyHash = "d", installationId = "i",
        packageName = "com.distrigo.app", plan = "solo", features = emptySet(),
        validFrom = issued - 5 * MINUTE, validTo = validTo, graceUntil = graceUntil,
        issuedAt = issued, offlineUntil = graceUntil, maxReboots = 30, seq = 1, token = "",
    )

    private fun at(floor: Long, wall: Long = floor, reboots: Int = 0) = TrustedTime.Known(
        wall, AnchorState(floor = floor, elapsed = 0, boot = 1, reboots = reboots, anchoredAt = issued, maxSeq = 1),
    )

    private fun judge(time: TrustedTime, license: License = this.license) = LicenseRules.judge(license, time)

    private fun assertState(expected: KClass<out LicenseState>, state: LicenseState) =
        assertEquals("$state", expected, state::class)

    @Test
    fun `active until a week before the end, then renew soon, recording all along`() {
        assertState(LicenseState.Active::class, judge(at(issued)))
        assertState(LicenseState.Active::class, judge(at(validTo - 7 * DAY)))
        assertState(LicenseState.RenewSoon::class, judge(at(validTo - 7 * DAY + 1)))
        val last = judge(at(validTo))
        assertState(LicenseState.RenewSoon::class, last)
        assertTrue(last.canRecord)
    }

    @Test
    fun `grace from the first millisecond after the end, expired after the last of grace`() {
        val grace = judge(at(validTo + 1))
        assertState(LicenseState.Grace::class, grace)
        assertTrue(grace.canRecord)
        assertState(LicenseState.Grace::class, judge(at(graceUntil)))
        val expired = judge(at(graceUntil + 1))
        assertState(LicenseState.Expired::class, expired)
        assertFalse(expired.canRecord)
        assertTrue((expired as LicenseState.Expired).certain)
    }

    @Test
    fun `expired by the phone's date alone is not certain`() {
        val state = judge(at(floor = validTo, wall = graceUntil + DAY))
        assertState(LicenseState.Expired::class, state)
        assertFalse((state as LicenseState.Expired).certain)
    }

    @Test
    fun `check in soon three days ahead, required past the offline window`() {
        val window = license.copy(offlineUntil = issued + 14 * DAY)
        assertState(LicenseState.Active::class, judge(at(window.offlineUntil - 3 * DAY), window))
        assertState(LicenseState.CheckInSoon::class, judge(at(window.offlineUntil - 3 * DAY + 1), window))
        assertState(LicenseState.CheckInSoon::class, judge(at(window.offlineUntil), window))
        val required = judge(at(window.offlineUntil + 1), window)
        assertEquals(LicenseState.CheckInRequired(window, CheckInReason.OFFLINE_TOO_LONG), required)
        assertFalse(required.canRecord)
    }

    @Test
    fun `the reboots the license allows, and not one more`() {
        assertState(LicenseState.Active::class, judge(at(issued, reboots = 30)))
        assertEquals(
            LicenseState.CheckInRequired(license, CheckInReason.TOO_MANY_REBOOTS),
            judge(at(issued, reboots = 31)),
        )
    }

    @Test
    fun `a date a little behind warns, more than a day behind stops recording`() {
        val tenMinutes = judge(at(issued + DAY, wall = issued + DAY - 10 * MINUTE)) as LicenseState.Judged
        assertFalse(tenMinutes.clockWarning)
        val behind = judge(at(issued + DAY, wall = issued + DAY - 10 * MINUTE - 1)) as LicenseState.Judged
        assertState(LicenseState.Active::class, behind)
        assertTrue(behind.clockWarning)
        assertTrue(behind.canRecord)
        assertState(LicenseState.Active::class, judge(at(issued + 2 * DAY, wall = issued + DAY)))
        val blocked = judge(at(issued + 2 * DAY, wall = issued + DAY - 1))
        assertState(LicenseState.ClockBehind::class, blocked)
        assertFalse(blocked.canRecord)
    }

    @Test
    fun `before the subscription starts nothing is recorded`() {
        val state = judge(at(license.validFrom - 1))
        assertState(LicenseState.NotYetValid::class, state)
        assertFalse(state.canRecord)
    }

    @Test
    fun `without a time it can vouch for, only a check-in can tell`() {
        assertEquals(
            LicenseState.CheckInRequired(license, CheckInReason.CLOCK_STATE_LOST),
            judge(TrustedTime.Unknown),
        )
    }

    @Test
    fun `when several apply, expiry comes first, then a date set back, then the check-in`() {
        val window = license.copy(offlineUntil = issued + 14 * DAY)
        // Expired, a date set back and the window passed, all at once.
        assertState(LicenseState.Expired::class, judge(at(graceUntil + DAY, wall = issued), window))
        // A date set back and the window passed.
        assertState(LicenseState.ClockBehind::class, judge(at(issued + 20 * DAY, wall = issued), window))
        // The window passed and too many reboots.
        assertEquals(
            CheckInReason.OFFLINE_TOO_LONG,
            (judge(at(issued + 20 * DAY, reboots = 99), window) as LicenseState.CheckInRequired).why,
        )
        // Grace while the check-in is near: grace is what the banner says.
        assertState(LicenseState.Grace::class, judge(at(validTo + 1)))
    }

    @Test
    fun `only the states before the end of grace record`() {
        val recording = listOf(
            judge(at(issued)), judge(at(validTo)), judge(at(validTo + 1)),
            judge(at(issued + 13 * DAY), license.copy(offlineUntil = issued + 14 * DAY)),
        )
        assertTrue(recording.all { it.canRecord })
        val stopped = listOf(
            LicenseState.Unchecked, LicenseState.NoLicense(), LicenseState.Revoked,
            LicenseState.CheckInRequired(null, CheckInReason.UNREADABLE),
            judge(at(graceUntil + 1)), judge(at(issued + 3 * DAY, wall = issued)), judge(at(issued - DAY)),
        )
        assertTrue(stopped.none { it.canRecord })
    }
}
