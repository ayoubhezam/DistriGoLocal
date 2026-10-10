package com.distrigo.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.random.Random

/** The floor of docs/license_architecture.md §3, against a phone whose real time the test knows. */
class TrustedClockTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val start = Instant.parse("2026-10-10T08:00:00Z").toEpochMilli()
    private val phone = FakePhone(start)
    private val store by lazy { LicenseStore(folder.root, HmacSeal()) }
    private val clock by lazy { TrustedClock(store, phone) }

    private fun anchored() = clock.also { it.anchor(start, seq = 7) }

    private fun read(on: TrustedClock = clock): TrustedTime.Known {
        val time = on.read()
        assertTrue("expected a known time, got $time", time is TrustedTime.Known)
        return time as TrustedTime.Known
    }

    @Test
    fun `without an anchor the time is unknown`() {
        assertEquals(TrustedTime.Unknown, clock.read())
    }

    @Test
    fun `the floor follows the monotonic clock, asleep or awake`() {
        anchored()
        phone.setDate(phone.real) // automatic time off: only the monotonic clock moves the floor
        phone.pass(5 * HOUR)
        assertEquals(start + 5 * HOUR, read().floor)
        phone.pass(2 * DAY)
        assertEquals(start + 5 * HOUR + 2 * DAY, read().floor)
    }

    @Test
    fun `a date set back does not move the floor back, it is measured against it`() {
        anchored()
        phone.pass(3 * DAY)
        read()
        phone.setDate(phone.real - 2 * DAY)
        val time = read()
        assertEquals(start + 3 * DAY, time.floor)
        assertEquals(2 * DAY, time.behindBy)
        assertEquals(time.floor, time.effective)
    }

    @Test
    fun `a reboot adds the time since boot and counts one reboot`() {
        anchored()
        phone.setDate(phone.real)
        phone.pass(2 * HOUR)
        read()                        // checkpoint
        phone.pass(1 * HOUR)          // lost: after the last checkpoint
        phone.switchOff(8 * HOUR)     // lost: switched off
        phone.pass(30 * MINUTE)
        val time = read()
        assertEquals(start + 2 * HOUR + 30 * MINUTE, time.floor)
        assertEquals(1, time.rebootsSinceCheckIn)
        assertTrue(time.floor <= phone.real)
    }

    @Test
    fun `without a boot count, a reboot shows as the monotonic clock going back`() {
        phone.bootCountKnown = false
        anchored()
        phone.setDate(phone.real)
        phone.pass(2 * HOUR)
        read()
        phone.switchOff(8 * HOUR)
        phone.pass(30 * MINUTE)
        val time = read()
        assertEquals(start + 2 * HOUR + 30 * MINUTE, time.floor)
        assertEquals(1, time.rebootsSinceCheckIn)
    }

    @Test
    fun `a reboot nobody noticed still leaves the floor below the real time`() {
        phone.bootCountKnown = false
        anchored()                    // the phone has been up an hour
        phone.setDate(phone.real)
        phone.pass(1 * HOUR)
        read()                        // up two hours at this checkpoint
        phone.switchOff(5 * MINUTE)
        phone.pass(3 * HOUR)          // up three hours again: the monotonic clock looks like it never went back
        val time = read()
        assertEquals(0, time.rebootsSinceCheckIn)
        assertEquals(start + 2 * HOUR, time.floor)
        assertTrue(time.floor <= phone.real)
    }

    @Test
    fun `automatic time catches up the hours switched off, a date set by hand never moves the floor`() {
        anchored()
        phone.setDate(phone.real + 10 * DAY)
        assertEquals(start, read().floor)          // ten days ahead by hand: ignored
        phone.setDate(phone.real)
        phone.switchOff(2 * DAY)
        assertEquals(start, read().floor)          // two days off: invisible to the monotonic clock
        phone.automaticDate()
        assertEquals(phone.real, read().floor)     // the network's date brings them back
    }

    @Test
    fun `the network's time moves the floor forward`() {
        anchored()
        phone.setDate(phone.real - DAY)
        phone.switchOff(2 * DAY)
        phone.networkTimeAvailable = true
        assertEquals(phone.real, read().floor)
    }

    @Test
    fun `a check-in moves the floor back to the server's time`() {
        anchored()
        phone.networkGlitch(ahead = 3 * DAY)       // a carrier sends a date three days ahead
        assertEquals(phone.real + 3 * DAY, read().floor)
        phone.automaticDate()
        clock.anchor(phone.real, seq = 8)          // only the server may move it back
        val time = read()
        assertEquals(phone.real, time.floor)
        assertEquals(0L, time.behindBy)
        assertEquals(8L, time.anchor.maxSeq)
    }

    @Test
    fun `the checkpoint survives a restart of the app`() {
        anchored()
        phone.setDate(phone.real)
        phone.pass(3 * HOUR)
        read()
        val restarted = TrustedClock(LicenseStore(folder.root, HmacSeal()), phone)
        phone.pass(1 * HOUR)
        assertEquals(start + 4 * HOUR, read(restarted).floor)
    }

    @Test
    fun `an edited state, or one sealed by another key, is not vouched for`() {
        anchored()
        val file = File(folder.root, "anchor.state")
        val (body, tag) = file.readLines()
        file.writeText(body.replace(Regex("\"floor\":\\d+"), "\"floor\":0") + "\n" + tag + "\n")
        assertEquals(TrustedTime.Unknown, clock.read())

        anchored()
        assertEquals(TrustedTime.Unknown, TrustedClock(LicenseStore(folder.root, HmacSeal(seed = 2)), phone).read())

        file.delete()
        assertEquals(TrustedTime.Unknown, clock.read())
    }

    /**
     * Whatever the user does with the date, the network and the power: the floor never passes the real time,
     * never goes back, and lags the real time by no more than the time the phone spent switched off.
     */
    @Test
    fun `the floor is a true lower bound through thousands of random days`() {
        for (seed in 1..20) {
            val phone = FakePhone(start)
            val clock = TrustedClock(MemoryAnchorStore(), phone)
            clock.anchor(start, seq = 1)
            val random = Random(seed)
            var switchedOff = 0L
            var previous = start
            repeat(500) { step ->
                when (random.nextInt(5)) {
                    0 -> phone.pass(random.nextLong(1, 6 * HOUR))
                    1 -> random.nextLong(1, 12 * HOUR).let { phone.switchOff(it); switchedOff += it }
                    2 -> phone.setDate(phone.real + random.nextLong(-30 * DAY, 30 * DAY))
                    3 -> phone.automaticDate()
                    else -> phone.networkTimeAvailable = random.nextBoolean()
                }
                val floor = (clock.read() as TrustedTime.Known).floor
                val where = "seed $seed, step $step"
                assertTrue("$where: floor past the real time", floor <= phone.real)
                assertTrue("$where: floor went back", floor >= previous)
                assertTrue("$where: floor lags more than the time switched off", floor >= phone.real - switchedOff)
                previous = floor
            }
        }
    }

    /** Without a boot count a reboot can go unseen, so the lag bound does not hold; the floor stays below the real time. */
    @Test
    fun `without a boot count the floor still never passes the real time`() {
        for (seed in 1..20) {
            val phone = FakePhone(start).apply { bootCountKnown = false }
            val clock = TrustedClock(MemoryAnchorStore(), phone)
            clock.anchor(start, seq = 1)
            val random = Random(seed)
            var previous = start
            repeat(500) { step ->
                when (random.nextInt(4)) {
                    0 -> phone.pass(random.nextLong(1, 6 * HOUR))
                    1 -> phone.switchOff(random.nextLong(1, 12 * HOUR))
                    2 -> phone.setDate(phone.real + random.nextLong(-30 * DAY, 30 * DAY))
                    else -> if (random.nextInt(4) == 0) phone.automaticDate()
                }
                val floor = (clock.read() as TrustedTime.Known).floor
                assertTrue("seed $seed, step $step", floor in previous..phone.real)
                previous = floor
            }
        }
    }
}
