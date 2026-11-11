package com.distrigo.app.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * The highest each document counter has reached on this phone, kept outside the database (BAK-01, audit 2026-11-09).
 *
 * The counters ([DocumentNumberTriggers]) live in `app_meta` and travel with the database, so restoring yesterday's
 * backup on the same phone brought yesterday's counters back, and the next sales were numbered again with numbers
 * already printed on receipts handed out. This file is in `no_backup`: it is never in a backup, never restored, and
 * stays with the phone — where the device prefix of every number it issues comes from too.
 *
 * Kept high in two places: each time the database opens ([withDocumentCounterFloor]), which then raises any counter
 * below its mark; and just before a restore replaces the database ([rememberFrom], from RestoreInstaller), which
 * catches the documents made since that open.
 *
 * One `key=value` line per counter, the keys of [DocumentNumberTriggers.counterKey]. A line it cannot read is
 * skipped: the worst a lost mark does is what happened before it existed.
 */
class DocumentCounterMarks(private val file: File) {

    /** Each counter's mark; empty when nothing was recorded yet. */
    fun read(): Map<String, Long> = synchronized(LOCK) {
        if (!file.isFile) return emptyMap()
        try {
            file.readLines().mapNotNull { line ->
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim().toLongOrNull()
                if (key.isNotEmpty() && value != null && value >= 0) key to value else null
            }.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "counter marks unreadable: $e")
            emptyMap()
        }
    }

    /** Raises each mark to [counters] where they are higher; a mark is never lowered. Returns the marks now kept. */
    fun raise(counters: Map<String, Long>): Map<String, Long> = synchronized(LOCK) {
        val kept = read()
        val merged = (kept.keys + counters.keys).associateWith { maxOf(kept[it] ?: 0L, counters[it] ?: 0L) }
        if (merged != kept) {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(merged.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}\n" })
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) throw java.io.IOException("cannot write $file")
            }
        }
        merged
    }

    /**
     * Records the counters of the database at [databaseFile] — the data a restore is about to replace. Never throws: a
     * restore must not fail on its account; the marks the last open recorded still stand.
     */
    fun rememberFrom(databaseFile: File) {
        if (!databaseFile.isFile) return
        try {
            val sql = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            val counters = try {
                sql.rawQuery("SELECT `key`, `value` FROM `app_meta` WHERE `key` LIKE 'numbering.%'", null).use { c ->
                    buildMap { while (c.moveToNext()) c.getString(1)?.toLongOrNull()?.let { put(c.getString(0), it) } }
                }
            } finally {
                sql.close()
            }
            raise(counters)
        } catch (e: Exception) {
            Log.w(TAG, "could not record the counters before a restore: $e")
        }
    }

    companion object {
        private const val TAG = "DocumentCounterMarks"
        private val LOCK = Any()

        fun forApp(context: Context) = DocumentCounterMarks(File(context.applicationContext.noBackupFilesDir, "numbering/high-water"))

        /** The counters in [db]'s `app_meta`. */
        internal fun counters(db: SupportSQLiteDatabase): Map<String, Long> =
            db.query("SELECT `key`, `value` FROM `app_meta` WHERE `key` LIKE 'numbering.%'").use { c ->
                buildMap { while (c.moveToNext()) c.getString(1)?.toLongOrNull()?.let { put(c.getString(0), it) } }
            }
    }
}

/**
 * Each time the database opens, every document counter is set to the larger of its own value and this phone's mark
 * ([DocumentCounterMarks]), and the marks follow the counters: a backup restored, older than the numbers the phone has
 * already issued, continues after them instead of issuing them again. Added after [withChangeTracking], which creates
 * any missing counter first.
 */
internal fun RoomDatabase.Builder<AppDatabase>.withDocumentCounterFloor(marks: DocumentCounterMarks): RoomDatabase.Builder<AppDatabase> =
    addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            try {
                val floor = marks.read()
                val counters = DocumentCounterMarks.counters(db)
                for ((key, mark) in floor) {
                    val counter = counters[key] ?: continue
                    if (mark > counter) {
                        db.execSQL("UPDATE `app_meta` SET `value` = ? WHERE `key` = ?", arrayOf<Any>(mark.toString(), key))
                    }
                }
                marks.raise(counters)
            } catch (e: Exception) {
                // The app must open whatever happens to this file; the numbers then run as they did before it
                Log.w("DocumentCounterMarks", "counter floor not applied: $e")
            }
        }
    })
