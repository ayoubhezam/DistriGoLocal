package com.distrigo.app.data.license

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/** The phone's clocks, as the license reads them. An interface so the tests can play a phone. */
interface Clocks {
    /** Milliseconds since boot, deep sleep included. Nobody can set it; it restarts at zero on a reboot. */
    fun elapsedRealtime(): Long

    /** Boots since the phone was set up, or null where the system does not keep the count. */
    fun bootCount(): Int?

    /** What the phone says the time is. Anyone can change it in the settings. */
    fun wall(): Long

    /** Whether the phone takes its time from the network ("Date et heure automatiques"). */
    fun autoTime(): Boolean

    /** The time Android last got from the network (NITZ/NTP), carried forward; null when unknown or before API 33. */
    fun networkTime(): Long?
}

class AndroidClocks(context: Context) : Clocks {

    private val resolver = context.applicationContext.contentResolver

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int? = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }

    override fun wall(): Long = System.currentTimeMillis()

    override fun autoTime(): Boolean = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 0) == 1

    override fun networkTime(): Long? =
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                SystemClock.currentNetworkTimeClock().millis()
            } catch (e: RuntimeException) {
                // DateTimeException: no network time since boot.
                null
            }
        } else {
            null
        }
}

/**
 * What this phone knows since its last check-in, sealed in LicenseStore so it cannot be rolled back.
 */
data class AnchorState(
    /** A proven lower bound on the real time when [elapsed] was read. */
    val floor: Long,
    /** `elapsedRealtime()` at the checkpoint. */
    val elapsed: Long,
    /** Boot count at the checkpoint, null where the system keeps none. */
    val boot: Int?,
    /** Reboots seen since the last check-in. */
    val reboots: Int,
    /** The server time of the last check-in. */
    val anchoredAt: Long,
    /** The highest license `seq` accepted. */
    val maxSeq: Long,
)

/** Where [TrustedClock] keeps its [AnchorState]: null on load when there is none, or it was altered. */
interface AnchorStore {
    fun load(): AnchorState?
    fun save(state: AnchorState)
}

/** What [TrustedClock] can vouch for. */
sealed interface TrustedTime {

    /** No state, or a state that was altered: only a check-in can say what time it is. */
    data object Unknown : TrustedTime

    data class Known(
        /** What the phone says. */
        val wall: Long,
        /** The state after this reading's checkpoint. */
        val anchor: AnchorState,
    ) : TrustedTime {
        /** The time the license has certainly reached. */
        val floor: Long get() = anchor.floor

        /** The time a license is judged at: the later of the two, so moving the date back gains nothing. */
        val effective: Long get() = maxOf(floor, wall)

        /** How far the phone's date is behind the time it has certainly reached: > 0 after a rollback. */
        val behindBy: Long get() = (floor - wall).coerceAtLeast(0)

        val rebootsSinceCheckIn: Int get() = anchor.reboots
    }
}

/**
 * The time, as far as this phone can prove it offline: docs/license_architecture.md §3.
 *
 * The anchor is the server's signed time at the last check-in. From there `elapsedRealtime()` carries a
 * *floor* forward: a time the real one has certainly reached, which nothing on the phone can turn back.
 *
 *  - **Any source may move the floor forward**: the network's time, and the phone's date when the phone
 *    took it from the network. A later time only makes a license older, which helps no one cheat; a wrong
 *    one heals at the next check-in.
 *  - **Only a check-in moves it back** ([anchor]), to the server's signed time.
 *  - **A date set by hand never moves it.** It is compared with it instead: [TrustedTime.Known.behindBy].
 *
 * What a monotonic clock cannot see is the time the phone spends switched off. After a reboot the floor
 * moves on by the time since boot only, so a phone switched off and set back gains its hours switched off,
 * within one offline window: the next check-in resets the floor to the server's time, and the reboot count
 * ends the window early for a phone rebooted again and again.
 */
class TrustedClock(private val store: AnchorStore, private val clocks: Clocks) {

    /** Reads the time and saves the checkpoint, so a reboot later loses only what follows. */
    fun read(): TrustedTime {
        val last = store.load() ?: return TrustedTime.Unknown
        val elapsed = clocks.elapsedRealtime()
        val boot = clocks.bootCount()
        // A reboot is certain when the boot count moved or the monotonic clock went back. When neither shows
        // one, the floor still holds: at least (elapsed − last.elapsed) has passed, even across a reboot unseen.
        val sameBoot = elapsed >= last.elapsed && (boot == null || last.boot == null || boot == last.boot)
        val reboots = when {
            boot != null && last.boot != null && boot > last.boot -> boot - last.boot
            !sameBoot -> 1
            else -> 0
        }
        var floor = last.floor + if (sameBoot) elapsed - last.elapsed else elapsed
        clocks.networkTime()?.let { floor = maxOf(floor, it) }
        val wall = clocks.wall()
        if (clocks.autoTime()) floor = maxOf(floor, wall)

        val next = last.copy(floor = floor, elapsed = elapsed, boot = boot, reboots = last.reboots + reboots)
        store.save(next)
        return TrustedTime.Known(wall, next)
    }

    /** A check-in: the server's [issuedAt] becomes the floor, even below the current one. */
    fun anchor(issuedAt: Long, seq: Long) {
        store.save(
            AnchorState(
                floor = issuedAt,
                elapsed = clocks.elapsedRealtime(),
                boot = clocks.bootCount(),
                reboots = 0,
                anchoredAt = issuedAt,
                maxSeq = seq,
            )
        )
    }
}
