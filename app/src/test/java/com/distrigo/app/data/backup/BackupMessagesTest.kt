package com.distrigo.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMessagesTest {

    private val problems = listOf(
        BackupProblem.CannotOpen,
        BackupProblem.NotABackup,
        BackupProblem.NewerFormat(2),
        BackupProblem.NewerDatabase(53, 52),
        BackupProblem.DatabaseTooOld(31, 32),
        BackupProblem.Damaged("distrigo.db checksum"),
    )

    @Test
    fun `every problem and failure has a message`() {
        val results = listOf(true, false).map { RestoreResult(it, java.time.Instant.EPOCH, null, null, null) }
        val messages = problems.map(BackupMessages::of) + BackupFailedException.Reason.entries.map(BackupMessages::of) +
            BackupFailedException.Reason.entries.map(BackupMessages::safetyBackupFailed) + results.map(BackupMessages::of)
        for (message in messages) {
            assertTrue(message, message.length > 30 && message.endsWith("."))
        }
    }

    /** Checksums, versions and file names are for the logs, not for the person restoring their data. */
    @Test
    fun `no technical detail reaches the user`() {
        for (problem in problems) {
            val message = BackupMessages.of(problem)
            for (detail in listOf("checksum", "53", "52", "31", "32", "manifest", "sha", "schema")) {
                assertFalse("$problem: $message", detail in message.lowercase())
            }
        }
    }

    @Test
    fun `not enough space says how much to free, rounded up to whole megabytes`() {
        assertEquals(1L, BackupMessages.megabytes(1))
        assertEquals(1L, BackupMessages.megabytes(1L shl 20))
        assertEquals(2L, BackupMessages.megabytes((1L shl 20) + 1))
        assertTrue("au moins 18 Mo" in BackupMessages.of(BackupProblem.NotEnoughSpace(18_500_000)))
        val unknown = BackupMessages.of(BackupProblem.NotEnoughSpace(0))
        assertTrue(unknown, unknown.endsWith(".") && unknown.none { it.isDigit() })
    }

    @Test
    fun `a newer backup asks for an update, whichever part is newer`() {
        assertEquals(BackupMessages.of(BackupProblem.NewerFormat(2)), BackupMessages.of(BackupProblem.NewerDatabase(53, 52)))
        assertTrue("mettez l'application à jour" in BackupMessages.of(BackupProblem.NewerFormat(2)).lowercase())
    }

    @Test
    fun `a failed restore says the data did not change`() {
        val failed = BackupMessages.of(RestoreResult(false, java.time.Instant.EPOCH, null, null, "quick_check: page 3"))
        assertTrue(failed, "n'ont pas changé" in failed && "quick_check" !in failed)
    }
}
