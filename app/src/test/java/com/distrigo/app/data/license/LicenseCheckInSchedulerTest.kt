package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the 12-hour schedule exists (docs/license_l4_checkin.md §4): it follows the account and the license. */
@OptIn(ExperimentalCoroutinesApi::class)
class LicenseCheckInSchedulerTest {

    private val license = (LicenseVerifier(Golden.trusted).verify(Golden.token("valid"), Golden.binding()) as LicenseCheck.Valid).license
    private val time = TrustedTime.Known(Golden.issuedAt, AnchorState(Golden.issuedAt, 0, null, 0, Golden.issuedAt, 7))
    private val active = LicenseState.Active(license, time)
    private val signedIn = AccountState.SignedIn(session())

    private val account = MutableStateFlow<AccountState>(AccountState.Loading)
    private val state = MutableStateFlow<LicenseState>(LicenseState.Unchecked)
    private val calls = mutableListOf<String>()

    private fun TestScope.following() {
        backgroundScope.launch { LicenseCheckInScheduler.follow(account, state, { calls += "schedule" }, { calls += "cancel" }) }
        runCurrent()
    }

    /** One change at a time, each seen by the schedule before the next. */
    private fun TestScope.becomes(account: AccountState? = null, license: LicenseState? = null) {
        account?.let { this@LicenseCheckInSchedulerTest.account.value = it }
        license?.let { state.value = it }
        runCurrent()
    }

    @Test
    fun `scheduled at each start while a signed-in account holds a license, kept through a suspension`() = runTest {
        following()
        assertEquals("nothing before the session and the license are read", emptyList<String>(), calls)
        becomes(account = signedIn)
        becomes(license = active)
        assertEquals(listOf("schedule"), calls)

        becomes(license = LicenseState.CheckInSoon(license, time))
        becomes(license = LicenseState.Suspended(license))
        becomes(license = LicenseState.CheckInRequired(null, CheckInReason.UNREADABLE))
        assertEquals("the reactivation is picked up by the same schedule", listOf("schedule"), calls)
    }

    @Test
    fun `cancelled by a revocation and a sign-out, and back with the next activation`() = runTest {
        becomes(account = signedIn, license = active)
        following()
        becomes(license = LicenseState.Revoked)
        assertEquals(listOf("schedule", "cancel"), calls)

        becomes(license = active)
        becomes(account = AccountState.SignedOut)
        assertEquals(listOf("schedule", "cancel", "schedule", "cancel"), calls)

        // Signing out released the seat and forgot the license; the next account activates
        becomes(license = LicenseState.NoLicense())
        becomes(account = signedIn)
        assertEquals(4, calls.size)
        becomes(license = active)
        assertEquals(listOf("schedule", "cancel", "schedule", "cancel", "schedule"), calls)
    }

    @Test
    fun `a phone without a license does not start WorkManager at launch`() = runTest {
        becomes(account = AccountState.SignedOut, license = LicenseState.NoLicense())
        following()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `only a phone signed out, or known to hold no license, or revoked, stops`() {
        assertEquals(null, LicenseCheckInScheduler.wanted(AccountState.Loading, active))
        assertEquals(null, LicenseCheckInScheduler.wanted(signedIn, LicenseState.Unchecked))
        assertEquals(true, LicenseCheckInScheduler.wanted(signedIn, LicenseState.Expired(license, time)))
        assertEquals(true, LicenseCheckInScheduler.wanted(signedIn, LicenseState.CheckInRequired(license, CheckInReason.OFFLINE_TOO_LONG)))
        assertEquals(false, LicenseCheckInScheduler.wanted(signedIn, LicenseState.NoLicense(Rejection.NO_DEVICE_KEY)))
        assertEquals(false, LicenseCheckInScheduler.wanted(signedIn, LicenseState.Revoked))
        assertEquals(false, LicenseCheckInScheduler.wanted(AccountState.SignedOut, active))
    }

    /** Decision 6 (docs/license_l5_enforcement.md): one notice when the subscription ends, while there is grace to announce. */
    @Test
    fun `the grace notice is planned at the end of the subscription held`() {
        assertEquals(license.validTo + 1_000, LicenseCheckInScheduler.graceNoticeAt(signedIn, active))
        assertEquals(license.validTo + 1_000, LicenseCheckInScheduler.graceNoticeAt(signedIn, LicenseState.Grace(license, time)))
        assertEquals("past the grace: nothing left to announce", null, LicenseCheckInScheduler.graceNoticeAt(signedIn, LicenseState.Expired(license, time)))
        assertEquals(null, LicenseCheckInScheduler.graceNoticeAt(signedIn, LicenseState.Suspended(license)))
        assertEquals(null, LicenseCheckInScheduler.graceNoticeAt(AccountState.SignedOut, active))
        assertEquals(null, LicenseCheckInScheduler.graceNoticeAt(signedIn, LicenseState.NoLicense()))
    }

    @Test
    fun `a renewal plans the notice again, the same end does not`() = runTest {
        val planned = mutableListOf<Long>()
        backgroundScope.launch {
            LicenseCheckInScheduler.followReminder(account, state, { planned += it }, { calls += "drop" }, LicenseCheckInScheduler::graceNoticeAt)
        }
        runCurrent()
        becomes(signedIn, active)
        becomes(license = LicenseState.Grace(license, time))
        val renewed = license.copy(validTo = license.validTo + 30 * 86_400_000L, graceUntil = license.graceUntil + 30 * 86_400_000L)
        becomes(license = LicenseState.Active(renewed, time))
        assertEquals(listOf(license.validTo + 1_000, renewed.validTo + 1_000), planned)
        becomes(license = LicenseState.Revoked)
        assertEquals(listOf("drop"), calls)
    }
}
