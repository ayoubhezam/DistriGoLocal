package com.distrigo.app.data.maintenance

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.distrigo.app.data.backup.RestoreInstaller
import com.distrigo.app.data.image.ImageReferences
import com.distrigo.app.data.image.ImageSweep
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.local.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What only grows unless something trims it (DB-02, audit 2026-11-09), trimmed at most once a day, at launch, off the
 * main thread: tombstones past [TOMBSTONE_HORIZON_DAYS], and the photo files nothing names any more ([ImageSweep]).
 *
 * Skipped while a restore waits to be installed: the database it brings may name photos the one on the phone does not.
 */
@Singleton
class Housekeeping @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val dir get() = File(context.noBackupFilesDir, "housekeeping")
    private val lastRun get() = File(dir, "last-run")

    fun runIfDue(nowMs: Long = System.currentTimeMillis()) {
        val last = try { lastRun.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() } catch (e: java.io.IOException) { null }
        // A clock set back counts as due, not as years ahead
        if (last != null && last in (nowMs - DUE_MS + 1)..nowMs) return
        if (RestoreInstaller.forApp(context).needsStart) return
        try {
            val sql = db.openHelper.writableDatabase
            val pruned = pruneTombstones(sql, nowMs)
            val sweep = ImageSweep(ImageStore.imagesDir(context), File(dir, "unreferenced-photos"))
                .run(ImageReferences.referencedHashes(sql), nowMs)
            dir.mkdirs()
            lastRun.writeText(nowMs.toString())
            Log.i(TAG, "tombstones pruned: $pruned; photos deleted: ${sweep.photosDeleted}, temp files: ${sweep.tempsDeleted}, waiting: ${sweep.waiting}")
        } catch (e: Exception) {
            // Nothing here is urgent: the next launch tries again
            Log.w(TAG, "housekeeping failed", e)
        }
    }

    companion object {
        private const val TAG = "Housekeeping"
        private const val DUE_MS = 20L * 60 * 60 * 1000

        /**
         * How long a deleted row's tombstone is kept. Nothing reads tombstones until sync exists; they are kept for it, to
         * tell another device what was deleted. 90 days covers a phone left offline through a long season, and a device
         * away longer than that would be brought back by a full copy rather than by replaying deletes. Sync will set
         * its own horizon (the last time every device was seen) when it comes.
         */
        const val TOMBSTONE_HORIZON_DAYS = 90L

        /** Deletes the tombstones older than the horizon; returns how many. */
        fun pruneTombstones(sql: SupportSQLiteDatabase, nowMs: Long): Int =
            sql.compileStatement("DELETE FROM tombstones WHERE deleted_at < ?").use {
                it.bindLong(1, nowMs - TOMBSTONE_HORIZON_DAYS * 24 * 60 * 60 * 1000)
                it.executeUpdateDelete()
            }
    }
}
