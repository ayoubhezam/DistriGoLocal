package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The app in front and what the merchant is told (docs/license_l4_checkin.md §3.4, §4, decisions 2, 3 and 6):
 * when coming to the front calls for a check-in, when the reminder comes, and when the battery hint shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LicenseInFrontTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val license = (LicenseVerifier(Golden.trusted).verify(Golden.token("valid"), Golden.binding()) as LicenseCheck.Valid).license
    private val time = TrustedTime.Known(Golden.issuedAt, AnchorState(Golden.issuedAt, 0, null, 0, Golden.issuedAt, 7))
    private val active = LicenseState.Active(license, time)
    private val signedIn = AccountState.SignedIn(session())

    // ── Coming to the front ──

    @Test
    fun `in front, a check-in is due six hours after the last license, or at once when the state asks for one`() {
        val issued = license.issuedAt
        assertFalse(LicenseInFront.dueInFront(signedIn, active, issued + 6 * HOUR))
        assertTrue(LicenseInFront.dueInFront(signedIn, active, issued + 6 * HOUR + MINUTE))

        assertTrue(LicenseInFront.dueInFront(signedIn, LicenseState.CheckInSoon(license, time), issued + MINUTE))
        assertTrue(LicenseInFront.dueInFront(signedIn, LicenseState.Suspended(license), issued + MINUTE))
        assertTrue(LicenseInFront.dueInFront(signedIn, LicenseState.CheckInRequired(license, CheckInReason.TOO_MANY_REBOOTS), issued + MINUTE))
    }

    @Test
    fun `in front, nothing is due without a license to check, or signed out`() {
        val late = license.issuedAt + DAY
        assertFalse(LicenseInFront.dueInFront(AccountState.SignedOut, active, late))
        assertFalse(LicenseInFront.dueInFront(signedIn, LicenseState.NoLicense(), late))
        assertFalse(LicenseInFront.dueInFront(signedIn, LicenseState.Revoked, late))
        assertFalse("nothing to send without a license read", LicenseInFront.dueInFront(signedIn, LicenseState.CheckInRequired(null, CheckInReason.UNREADABLE), late))
        assertFalse(LicenseInFront.dueInFront(AccountState.Loading, active, late))
    }

    // ── The reminder (decision 3) ──

    @Test
    fun `the reminder comes two days before the offline window ends, or half-way through a shorter one`() {
        assertEquals(license.offlineUntil - 2 * DAY, LicenseCheckInScheduler.remindAt(signedIn, active))
        val reduced = license.copy(offlineUntil = license.issuedAt + DAY)
        assertEquals(license.issuedAt + 12 * HOUR, LicenseCheckInScheduler.remindAt(signedIn, LicenseState.Active(reduced, time)))

        assertEquals("only DistriGo lifts a suspension", null, LicenseCheckInScheduler.remindAt(signedIn, LicenseState.Suspended(license)))
        assertEquals("signed out, a check-in can't help", null, LicenseCheckInScheduler.remindAt(AccountState.SignedOut, active))
        assertEquals(null, LicenseCheckInScheduler.remindAt(signedIn, LicenseState.NoLicense()))
    }

    @Test
    fun `the reminder is planned again for each license, and dropped when none applies`() = runTest {
        val account = MutableStateFlow<AccountState>(AccountState.Loading)
        val state = MutableStateFlow<LicenseState>(LicenseState.Unchecked)
        val calls = mutableListOf<String>()
        backgroundScope.launch {
            LicenseCheckInScheduler.followReminder(account, state, { calls += "remind ${it - license.issuedAt}" }, { calls += "drop" })
        }
        runCurrent()
        account.value = signedIn
        state.value = active
        runCurrent()
        // The same license read again plans nothing new
        state.value = LicenseState.CheckInSoon(license, time)
        runCurrent()
        val renewed = license.copy(issuedAt = license.issuedAt + 10 * DAY, offlineUntil = license.offlineUntil + 10 * DAY)
        state.value = LicenseState.Active(renewed, time)
        runCurrent()
        state.value = LicenseState.Suspended(renewed)
        runCurrent()
        assertEquals(listOf("remind ${12 * DAY}", "remind ${22 * DAY}", "drop"), calls)
    }

    // ── The battery hint (decision 6) ──

    @Test
    fun `the battery hint shows when the background has had no answer for three days while others got through`() {
        val since = 1_000_000L
        val scheduled = CheckInLog.Entry(scheduledSince = since)
        assertFalse("nothing got through: the phone may just be offline", CheckInLog.backgroundHeldBack(scheduled, since + 4 * DAY))
        val inFront = scheduled.copy(lastAnswerAt = since + 3 * DAY)
        assertFalse(CheckInLog.backgroundHeldBack(inFront, since + 3 * DAY))
        assertTrue(CheckInLog.backgroundHeldBack(inFront, since + 3 * DAY + MINUTE))

        val background = inFront.copy(lastBackgroundAnswerAt = since + 3 * DAY)
        assertFalse("the background answered last", CheckInLog.backgroundHeldBack(background, since + 5 * DAY))
        assertFalse(CheckInLog.backgroundHeldBack(CheckInLog.Entry(lastAnswerAt = since), since + 9 * DAY))
    }

    @Test
    fun `the schedule's start is kept across starts, and forgotten when it ends`() {
        val log = CheckInLog(folder.root)
        log.markScheduled(1_000)
        log.markScheduled(2_000)
        assertEquals(1_000L, CheckInLog(folder.root).read().scheduledSince)
        log.markUnscheduled()
        assertEquals(null, log.read().scheduledSince)
        log.markScheduled(3_000)
        assertEquals(3_000L, log.read().scheduledSince)
    }
}
