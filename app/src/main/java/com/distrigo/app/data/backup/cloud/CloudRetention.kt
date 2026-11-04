package com.distrigo.app.data.backup.cloud

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Which cloud backups to keep (plan §3.4, owner's decision §9.2): the newest of each of the last [DAYS] days and the
 * newest of each of the last [WEEKS] calendar weeks, in the phone's time.
 *
 * Weeks are fixed calendar weeks starting on Sunday (the Algerian working week), not blocks counted back from today:
 * with moving blocks, the backup kept as the newest of its block falls into the next block a day later and is
 * deleted, and the weeklies thin out. In a fixed week, the newest backup stays the newest for good.
 *
 * Never selected for deletion: [justSent] (the backup this run confirmed, even if another carries the same time), the
 * newest backup whatever its age, **the newest backup of each phone** (by `deviceId`), and anything dated in the
 * future (a phone whose clock was wrong: better kept than lost). Within a day or a week, [justSent] counts as the
 * newest.
 *
 * Per phone, because a lost phone's last backup is the one that matters most: a new phone linked the same day backs up
 * its still-empty data, and « the newest of the day » alone would delete the lost phone's real backup.
 */
object CloudRetention {
    const val DAYS = 7
    const val WEEKS = 4
    val WEEK_START: DayOfWeek = DayOfWeek.SUNDAY

    fun toDelete(entries: List<CloudBackupEntry>, now: Instant, zone: ZoneId, justSent: String? = null): List<CloudBackupEntry> {
        if (entries.isEmpty()) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        val thisWeek = weekOf(today)
        // Ties on the time: the backup just sent first, so it is the one a day or a week keeps.
        val newestFirst = entries.sortedWith(
            compareByDescending<CloudBackupEntry> { it.meta.createdAtMillis }.thenByDescending { it.id == justSent },
        )
        val keep = HashSet<String>()
        keep += newestFirst.first().id
        justSent?.let { keep += it }
        newestFirst.distinctBy { it.meta.deviceId }.forEach { keep += it.id }
        val dailyKept = HashSet<LocalDate>()
        val weeklyKept = HashSet<LocalDate>()
        for (entry in newestFirst) {
            val day = Instant.ofEpochMilli(entry.meta.createdAtMillis).atZone(zone).toLocalDate()
            if (day.isAfter(today)) {
                keep += entry.id
                continue
            }
            if (ChronoUnit.DAYS.between(day, today) < DAYS && dailyKept.add(day)) keep += entry.id
            val week = weekOf(day)
            if (ChronoUnit.WEEKS.between(week, thisWeek) < WEEKS && weeklyKept.add(week)) keep += entry.id
        }
        return newestFirst.filter { it.id !in keep }
    }

    /** The Sunday that starts [day]'s week. */
    fun weekOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(WEEK_START))
}
