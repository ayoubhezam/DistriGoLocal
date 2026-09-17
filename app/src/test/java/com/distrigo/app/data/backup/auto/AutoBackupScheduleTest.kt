package com.distrigo.app.data.backup.auto

import com.distrigo.app.data.backup.BackupFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class AutoBackupScheduleTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    @Test
    fun `the first run is at the next three o'clock local time`() {
        // 14:00 in Algeria: tonight at 03:00, 13 hours on.
        assertEquals(Duration.ofHours(13), AutoBackupScheduler.delayUntilNextRun(Instant.parse("2026-09-17T13:00:00Z"), algiers))
        // 01:30 in Algeria: 03:00 the same night.
        assertEquals(Duration.ofMinutes(90), AutoBackupScheduler.delayUntilNextRun(Instant.parse("2026-09-17T00:30:00Z"), algiers))
        // Exactly 03:00: the next day's, not now.
        assertEquals(Duration.ofHours(24), AutoBackupScheduler.delayUntilNextRun(Instant.parse("2026-09-17T02:00:00Z"), algiers))
    }

    /** Where clocks change, 03:00 is still 03:00 on the wall. */
    @Test
    fun `a daylight saving night still lands on three o'clock`() {
        val paris = ZoneId.of("Europe/Paris")
        // 29 March 2026: 02:00 becomes 03:00. From 22:00 the evening before, 03:00 comes 4 hours later, not 5.
        val evening = Instant.parse("2026-03-28T21:00:00Z")
        val delay = AutoBackupScheduler.delayUntilNextRun(evening, paris)
        assertEquals(Duration.ofHours(4), delay)
        assertEquals(3, evening.plus(delay).atZone(paris).hour)
    }

    @Test
    fun `a problem is a failure, or a run that could not use the chosen folder`() {
        val saved = AutoBackupOutcome.Saved("f", 1, "DistriGo", inPrivateStorage = false, removed = 0)
        val inApp = saved.copy(inPrivateStorage = true)
        val failed = AutoBackupOutcome.Failed(BackupFailedException.Reason.WRITE_FAILED, "storage full")

        assertNull(AutoBackupProblem.after(saved, folderChosen = true, folderUsable = true))
        assertNull(AutoBackupProblem.after(AutoBackupOutcome.Unchanged, folderChosen = true, folderUsable = true))
        assertEquals(AutoBackupProblem.WRITE_FAILED, AutoBackupProblem.after(failed, folderChosen = true, folderUsable = true))
        assertEquals(AutoBackupProblem.FOLDER_UNAVAILABLE, AutoBackupProblem.after(inApp, folderChosen = true, folderUsable = false))
        assertEquals(
            "an unchanged day while the folder is lost is still a day without a backup in it",
            AutoBackupProblem.FOLDER_UNAVAILABLE, AutoBackupProblem.after(AutoBackupOutcome.Unchanged, folderChosen = true, folderUsable = false)
        )
        assertEquals(AutoBackupProblem.NO_FOLDER, AutoBackupProblem.after(inApp, folderChosen = false, folderUsable = false))
    }

    @Test
    fun `the user is alerted from the second problem in a row, only while backups are on`() {
        val one = AutoBackupState(enabled = true, problemStreak = 1, lastProblem = AutoBackupProblem.WRITE_FAILED)
        assertFalse(AutoBackupAlerts.shouldAlert(one))
        assertTrue(AutoBackupAlerts.shouldAlert(one.copy(problemStreak = 2)))
        assertTrue(AutoBackupAlerts.shouldAlert(one.copy(problemStreak = 9)))
        assertFalse(AutoBackupAlerts.shouldAlert(one.copy(problemStreak = 2, enabled = false)))
        assertFalse(AutoBackupAlerts.shouldAlert(one.copy(problemStreak = 0, lastProblem = null)))
    }

    @Test
    fun `every problem has a message for the user`() {
        for (problem in AutoBackupProblem.entries) {
            assertTrue(problem.name, problem.message.length > 30 && problem.message.endsWith("."))
        }
    }
}
