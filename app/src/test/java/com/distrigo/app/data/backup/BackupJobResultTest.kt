package com.distrigo.app.data.backup

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Test

/** A background backup's result, carried by WorkManager from the worker to the screen (2026-10-21). */
class BackupJobResultTest {

    // The words the user reads, from the French resources (FrenchStrings), as the phone writes them in French
    private val previousLocale = java.util.Locale.getDefault()
    @org.junit.Before fun french() { com.distrigo.app.testing.FrenchStrings.install(); java.util.Locale.setDefault(java.util.Locale.FRENCH) }
    @org.junit.After fun reset() { com.distrigo.app.testing.FrenchStrings.uninstall(); java.util.Locale.setDefault(previousLocale) }

    @Test
    fun everyResultReadsBackAsItWasWritten() {
        val results = listOf(
            BackupJobResult.Saved(fileName = "DistriGo-2026-10-21-0911.distrigo", size = 219_152_384, photos = 19, photosLeftOut = 2),
            BackupJobResult.Saved(fileName = "DistriGo-auto.distrigo", size = 1_024, folderName = "Sauvgarde distrigo", removed = 1, inPrivateStorage = true),
            BackupJobResult.Unchanged,
            BackupJobResult.Failed("Impossible d'écrire à cet endroit."),
        )
        for (result in results) assertEquals(result, BackupJobResult.of(result.toData()))
    }

    @Test
    fun anOutputWithoutAResultIsAStoppedBackup() {
        assertEquals(BackupJobResult.Failed(BackupJob.STOPPED), BackupJobResult.of(Data.EMPTY))
    }
}
