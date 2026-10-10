package com.distrigo.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.ProviderException
import kotlin.reflect.KClass

/**
 * The license end to end, offline: golden tokens from the server's side, the real files, and a phone whose
 * user tries what docs/license_architecture.md §0 says must fail.
 */
class LicenseManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** The server's answer arrives two seconds after it signed. */
    private val phone = FakePhone(Golden.issuedAt + 2 * SECOND)

    private fun manager(
        dir: File = folder.root,
        deviceHash: String? = Golden.deviceHash,
        installationId: String = Golden.installationId,
        seal: StateSeal = HmacSeal(),
    ): LicenseManager {
        val store = LicenseStore(dir, seal)
        return LicenseManager(
            store = store,
            verifier = LicenseVerifier(Golden.trusted),
            clock = TrustedClock(store, phone),
            deviceKeyHash = { deviceHash },
            installationId = { installationId },
            packageName = Golden.PACKAGE,
        )
    }

    private fun activated(): LicenseManager = manager().also {
        assertTrue(it.install(Golden.token("valid")) is LicenseCheck.Valid)
    }

    /** Activated, checked in again on 5 November (offline window to the end of grace), and run past the grace. */
    private fun expired(): LicenseManager = activated().also {
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        assertTrue(it.install(Golden.token("late")) is LicenseCheck.Valid)
        phone.passTo(Golden.graceUntil + SECOND)
        assertState(LicenseState.Expired::class, it.evaluate())
    }

    private fun assertState(expected: KClass<out LicenseState>, state: LicenseState) =
        assertEquals("$state", expected, state::class)

    private fun checkInReason(state: LicenseState) = (state as LicenseState.CheckInRequired).why

    // ── The ordinary life of a license ──

    @Test
    fun `a phone never activated records nothing`() {
        val state = manager().evaluate()
        assertEquals(LicenseState.NoLicense(), state)
        assertFalse(state.canRecord)
    }

    @Test
    fun `activation makes the phone active and anchors its clock on the server's time`() {
        val manager = manager()
        assertEquals(LicenseState.Unchecked, manager.state.value)
        assertTrue(manager.install(Golden.token("valid")) is LicenseCheck.Valid)
        val state = manager.state.value as LicenseState.Active
        assertEquals(7L, state.license.seq)
        assertEquals(Golden.issuedAt, state.time.anchor.anchoredAt)
        assertEquals(phone.real, state.time.floor)
        assertTrue(state.canRecord)
    }

    @Test
    fun `the phone is warned three days before its check-in is due, and stops at it`() {
        val manager = activated()
        phone.passTo(Golden.offlineUntil - 3 * DAY + MINUTE)
        val soon = manager.evaluate()
        assertState(LicenseState.CheckInSoon::class, soon)
        assertTrue(soon.canRecord)
        phone.passTo(Golden.offlineUntil + SECOND)
        val due = manager.evaluate()
        assertEquals(CheckInReason.OFFLINE_TOO_LONG, checkInReason(due))
        assertFalse(due.canRecord)
    }

    @Test
    fun `a check-in late in the subscription carries it through the grace days, then it stops`() {
        val manager = activated()
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        assertTrue(manager.install(Golden.token("late")) is LicenseCheck.Valid)
        assertState(LicenseState.RenewSoon::class, manager.state.value)
        phone.passTo(Golden.validTo + HOUR)
        val grace = manager.evaluate()
        assertState(LicenseState.Grace::class, grace)
        assertTrue(grace.canRecord)
        phone.passTo(Golden.graceUntil + SECOND)
        val expired = manager.evaluate() as LicenseState.Expired
        assertTrue(expired.certain)
        assertFalse(expired.canRecord)
    }

    @Test
    fun `an update or a restart keeps the license, nobody is signed out`() {
        activated()
        phone.pass(DAY)
        val afterUpdate = manager().evaluate() as LicenseState.Active
        assertEquals(7L, afterUpdate.license.seq)
        assertEquals(phone.real, afterUpdate.time.floor)
    }

    @Test
    fun `signing out forgets the license and the clock`() {
        val manager = activated()
        assertEquals(LicenseState.NoLicense(), manager.clear())
        assertFalse(File(folder.root, "license.jws").exists())
        assertFalse(File(folder.root, "anchor.state").exists())
    }

    @Test
    fun `a revoked phone stops at once, across restarts, until a newer license comes`() {
        val manager = activated()
        assertEquals(LicenseState.Revoked, manager.revoke())
        assertEquals(LicenseState.Revoked, manager().evaluate())
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        assertTrue(manager.install(Golden.token("late")) is LicenseCheck.Valid)
        assertTrue(manager.state.value.canRecord)
    }

    // ── What the user may try ──

    @Test
    fun `setting the date back after expiry does not bring the subscription back, even after a reboot`() {
        val manager = expired()
        phone.setDate(Golden.lateIssuedAt)
        assertTrue((manager.evaluate() as LicenseState.Expired).certain)
        phone.switchOff(HOUR)
        assertTrue((manager.evaluate() as LicenseState.Expired).certain)
        phone.switchOff(10 * MINUTE)
        phone.setDate(Golden.issuedAt)
        assertFalse(manager.evaluate().canRecord)
    }

    @Test
    fun `setting the date back during the subscription stops recording until the date is right`() {
        val manager = activated()
        phone.pass(5 * DAY)
        assertState(LicenseState.Active::class, manager.evaluate())
        phone.setDate(phone.real - HOUR)
        val warned = manager.evaluate() as LicenseState.Active
        assertTrue(warned.clockWarning)
        phone.setDate(phone.real - 4 * DAY)
        assertState(LicenseState.ClockBehind::class, manager.evaluate())
        phone.automaticDate()
        val fixed = manager.evaluate() as LicenseState.Active
        assertFalse(fixed.clockWarning)
    }

    @Test
    fun `a date set ahead by mistake and then corrected leaves no false alarm`() {
        val manager = activated()
        phone.setDate(phone.real + 40 * DAY)
        val ahead = manager.evaluate() as LicenseState.Expired
        assertFalse("only the phone's date says so", ahead.certain)
        phone.setDate(phone.real)
        val corrected = manager.evaluate() as LicenseState.Active
        assertFalse(corrected.clockWarning)
    }

    @Test
    fun `freezing the date every morning is caught after two days`() {
        val manager = activated()
        val frozen = phone.real
        var days = 0
        while (manager.evaluate().canRecord) {
            phone.pass(14 * HOUR)
            manager.evaluate()            // the app in use during the day
            phone.switchOff(10 * HOUR)
            phone.setDate(frozen)         // the same morning again
            days++
        }
        assertEquals(2, days)
        assertState(LicenseState.ClockBehind::class, manager.state.value)
    }

    /**
     * The best a user can do with the date: every morning, set it as far back as the app tolerates without
     * stopping. The license then ages with the hours the phone is on, not the hours it is off, and the offline
     * window still ends: 14 days of a phone on 14 hours a day last 24 days, all before the subscription ends.
     */
    @Test
    fun `holding the date back as far as tolerated only stretches the offline window`() {
        val manager = activated()
        var days = 0
        while (manager.evaluate().canRecord) {
            days++
            phone.pass(14 * HOUR)
            val evening = manager.evaluate() as? LicenseState.Judged ?: break
            phone.switchOff(10 * HOUR)
            phone.setDate(evening.time.floor - 23 * HOUR)
        }
        // Stopped during the 24th day, when the floor passed the offline window.
        assertEquals(24, days)
        assertEquals(CheckInReason.OFFLINE_TOO_LONG, checkInReason(manager.state.value))
        assertTrue(phone.real < Golden.validTo)
    }

    /**
     * Worse than any user can arrange: the app never sees the hours the phone is on, only a moment after each
     * boot, with the date frozen. The floor barely moves, so the reboot count ends it, before the grace ends.
     */
    @Test
    fun `when checkpoints are missed, the reboot count still ends the window`() {
        val manager = activated()
        val frozen = phone.real
        var reboots = 0
        while (manager.evaluate().canRecord) {
            phone.pass(14 * HOUR)
            phone.switchOff(10 * HOUR)
            phone.setDate(frozen)
            reboots++
        }
        assertEquals(31, reboots)
        assertEquals(CheckInReason.TOO_MANY_REBOOTS, checkInReason(manager.state.value))
        assertTrue(phone.real < Golden.graceUntil)
    }

    @Test
    fun `a reboot loop ends the window at the license's limit`() {
        val manager = activated()
        repeat(30) {
            phone.switchOff(MINUTE)
            assertTrue(manager.evaluate().canRecord)
        }
        phone.switchOff(MINUTE)
        assertEquals(CheckInReason.TOO_MANY_REBOOTS, checkInReason(manager.evaluate()))
    }

    @Test
    fun `a copy of the files on another phone carries the license but not the key`() {
        activated()
        val otherPhone = folder.newFolder("other-phone")
        folder.root.listFiles { file -> file.isFile }!!.forEach { it.copyTo(File(otherPhone, it.name)) }
        // Another phone's Keystore: its own device key and sealing key, or no device key at all.
        assertEquals(
            LicenseState.NoLicense(Rejection.WRONG_DEVICE),
            manager(otherPhone, deviceHash = "its-own-key", seal = HmacSeal(seed = 2)).evaluate(),
        )
        assertEquals(
            LicenseState.NoLicense(Rejection.NO_DEVICE_KEY),
            manager(otherPhone, deviceHash = null, seal = HmacSeal(seed = 2)).evaluate(),
        )
        // And its own installation id: the copy would be refused on that alone.
        assertEquals(
            LicenseState.NoLicense(Rejection.WRONG_INSTALLATION),
            manager(otherPhone, installationId = "c0ffee00-0000-4000-8000-000000000000").evaluate(),
        )
    }

    @Test
    fun `an edited clock reads as lost until the next check-in repairs it`() {
        val manager = activated()
        phone.pass(20 * DAY)
        val file = File(folder.root, "anchor.state")
        val (body, tag) = file.readLines()
        file.writeText(body.replace(Regex("\"floor\":\\d+"), "\"floor\":${Golden.issuedAt}") + "\n" + tag + "\n")
        assertEquals(CheckInReason.CLOCK_STATE_LOST, checkInReason(manager.evaluate()))

        file.delete()
        assertEquals(CheckInReason.CLOCK_STATE_LOST, checkInReason(manager.evaluate()))

        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        assertTrue(manager.install(Golden.token("late")) is LicenseCheck.Valid)
        assertTrue(manager.state.value.canRecord)
    }

    @Test
    fun `an older license is refused, installed or put back by hand`() {
        val manager = activated()
        phone.passTo(Golden.lateIssuedAt + 2 * SECOND)
        assertTrue(manager.install(Golden.token("late")) is LicenseCheck.Valid)

        assertEquals(LicenseCheck.Rejected(Rejection.REPLAYED), manager.install(Golden.token("valid")))
        assertEquals(LicenseCheck.Rejected(Rejection.REPLAYED), manager.install(Golden.token("late")))
        assertEquals(8L, (manager.state.value as LicenseState.Judged).license.seq)

        File(folder.root, "license.jws").writeText(Golden.token("valid"))
        assertEquals(LicenseState.NoLicense(Rejection.REPLAYED), manager.evaluate())
    }

    @Test
    fun `a forged license is refused before it touches the clock`() {
        val manager = activated()
        val anchor = File(folder.root, "anchor.state").readText()
        for (forged in listOf("tampered_payload", "alg_none", "alg_hs256", "unknown_kid", "other_device")) {
            assertTrue(forged, manager.install(Golden.token(forged)) is LicenseCheck.Rejected)
        }
        assertEquals(anchor, File(folder.root, "anchor.state").readText())
        assertState(LicenseState.Active::class, manager.state.value)
    }

    @Test
    fun `a stored license that is not this phone's says why`() {
        File(folder.root, "license.jws").apply { parentFile!!.mkdirs() }.writeText(Golden.token("other_device"))
        assertEquals(LicenseState.NoLicense(Rejection.WRONG_DEVICE), manager().evaluate())
    }

    @Test
    fun `without its device key the phone has no license and cannot install one`() {
        activated()
        val keyless = manager(deviceHash = null)
        assertEquals(LicenseState.NoLicense(Rejection.NO_DEVICE_KEY), keyless.evaluate())
        assertEquals(LicenseCheck.Rejected(Rejection.NO_DEVICE_KEY), keyless.install(Golden.token("late")))
    }

    @Test
    fun `Keystore trouble fails closed`() {
        activated()
        val broken = manager(seal = { throw ProviderException("Keystore unavailable") })
        val state = broken.evaluate() as LicenseState.CheckInRequired
        assertEquals(CheckInReason.UNREADABLE, state.why)
        assertTrue(state.error is ProviderException)
        assertFalse(state.canRecord)
    }
}
