package com.distrigo.app.data.backup

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.RoomDatabase
import com.distrigo.app.data.image.ImageStore
import java.io.File
import java.time.Instant

/** A consistent copy of the database, and what a backup needs to know about it. */
class Snapshot(
    /** One SQLite file with no journal beside it: `distrigo.db` in the staging folder. */
    val database: File,
    val createdAt: Instant,
    val schemaVersion: Int,
    val databaseId: String,
    val deviceId: String,
    val rowCounts: Map<String, Long>,
    /** Hashes of the photos the copy refers to that exist in the image folder, sorted. */
    val imageHashes: List<String>,
    /** Photos the copy refers to that are not on the phone. */
    val missingImages: Int,
)

/** The copy cannot be used: the database fails its integrity check, or lacks what every database has. */
class SnapshotException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Copies the live database while the app keeps running.
 *
 * ### Why the copy is consistent
 *
 * Android before 11 has no `VACUUM INTO`, so the copy is of the files. The journal is folded into the
 * main file first, then a write transaction is held while the main file and whatever journal is left
 * are copied. In WAL mode only a writer changes either file (readers never checkpoint), so with the
 * write lock held neither can change mid-copy; readers carry on, and writers wait a few milliseconds.
 * In rollback-journal mode, a reserved lock leaves the main file untouched until the next commit.
 *
 * The copied journal is folded into the copy, not into the live file, so a checkpoint the first step
 * could not finish — a long read in progress — costs nothing but a larger copy.
 *
 * ### What is checked
 *
 * The copy must pass `PRAGMA integrity_check` and carry `database_id` and `device_id`. Then the copy,
 * not the live database, is what the counts and photo references are read from, so they describe
 * exactly what is in the file.
 */
object DatabaseSnapshot {

    /**
     * Copies [db], whose file is [databaseFile], to [stagingDir]/`distrigo.db`, replacing any earlier
     * copy there, and looks up the photos it refers to in [imagesDir].
     *
     * Blocks, doing file I/O: call it off the main thread.
     */
    fun take(
        db: RoomDatabase,
        databaseFile: File,
        imagesDir: File,
        stagingDir: File,
        checkpointFirst: Boolean = true,
    ): Snapshot {
        stagingDir.mkdirs()
        val copy = File(stagingDir, BackupFormat.DATABASE_ENTRY)
        deleteWithJournals(copy)

        val live = db.openHelper.writableDatabase
        if (checkpointFirst) {
            live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }

        lateinit var createdAt: Instant
        db.runInTransaction {
            // Takes the write lock now rather than at the first write, whatever BEGIN Room used.
            live.execSQL("DELETE FROM app_meta WHERE 0")
            createdAt = Instant.now()
            databaseFile.copyTo(copy, overwrite = true)
            // Not -shm: it is the live connections' shared index, and SQLite rebuilds it from the -wal.
            for (suffix in COPIED_JOURNALS) {
                val journal = File(databaseFile.path + suffix)
                if (journal.isFile) journal.copyTo(File(copy.path + suffix), overwrite = true)
            }
        }

        return inspect(copy, imagesDir, createdAt).also { requireSingleFile(copy) }
    }

    /**
     * Folds the journal copied beside [copy] into it, checks it, and reads what a backup records.
     *
     * Opened without Android's default error handler, which deletes a database it finds corrupt.
     */
    internal fun inspect(copy: File, imagesDir: File, createdAt: Instant): Snapshot {
        val sql = try {
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS, KEEP_CORRUPT_FILE)
        } catch (e: SQLiteException) {
            throw SnapshotException("the copy does not open: ${e.message}", e)
        }
        try {
            sql.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            val mode = sql.text("PRAGMA journal_mode = DELETE")
            if (!mode.equals("delete", ignoreCase = true)) throw SnapshotException("the copy stays in $mode mode")

            val problems = sql.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            if (problems != listOf("ok")) {
                throw SnapshotException("integrity_check: " + problems.take(5).joinToString("; "))
            }

            val databaseId = sql.meta("database_id") ?: throw SnapshotException("no database_id")
            val deviceId = sql.meta("device_id") ?: throw SnapshotException("no device_id")

            val tables = userTables(sql)
            val rowCounts = tables.associateWith { sql.long("SELECT COUNT(*) FROM `$it`") }

            val referenced = sortedSetOf<String>()
            for (table in tables) {
                for (column in textColumns(sql, table)) {
                    sql.rawQuery("SELECT `$column` FROM `$table` WHERE instr(`$column`, '${ImageStore.REF_PREFIX}') > 0", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            IMAGE_REF.findAll(cursor.getString(0)).forEach { referenced += it.groupValues[1] }
                        }
                    }
                }
            }
            val present = referenced.filter { File(imagesDir, "$it.jpg").isFile }

            return Snapshot(
                database = copy,
                createdAt = createdAt,
                schemaVersion = sql.version,
                databaseId = databaseId,
                deviceId = deviceId,
                rowCounts = rowCounts,
                imageHashes = present,
                missingImages = referenced.size - present.size,
            )
        } catch (e: SQLiteException) {
            throw SnapshotException("the copy cannot be read: ${e.message}", e)
        } finally {
            sql.close()
        }
    }

    /** After [inspect] closed it, a copy must be one file: anything beside it would be data the file lacks. */
    internal fun requireSingleFile(copy: File) {
        val leftover = JOURNAL_SUFFIXES.map { File(copy.path + it) }.filter { it.isFile && it.length() > 0 }
        if (leftover.isNotEmpty()) throw SnapshotException("the copy still has ${leftover.joinToString { it.name }}")
    }

    /** Every table holding the app's data: not SQLite's, Android's or Room's own bookkeeping. */
    internal fun userTables(sql: SQLiteDatabase): List<String> =
        sql.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name", null
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun textColumns(sql: SQLiteDatabase, table: String): List<String> =
        sql.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val type = cursor.getColumnIndexOrThrow("type")
            buildList { while (cursor.moveToNext()) if (cursor.getString(type).equals("TEXT", ignoreCase = true)) add(cursor.getString(name)) }
        }

    fun deleteWithJournals(file: File) {
        file.delete()
        for (suffix in JOURNAL_SUFFIXES) File(file.path + suffix).delete()
    }

    private val JOURNAL_SUFFIXES = listOf("-wal", "-shm", "-journal")
    private val COPIED_JOURNALS = listOf("-wal", "-journal")

    /** An `img:` reference anywhere in a value, including inside a draft's JSON. */
    private val IMAGE_REF = Regex("${ImageStore.REF_PREFIX}([0-9a-f]{64})")

    /** Android's default handler deletes a database it finds corrupt; this one leaves it for the check to report. */
    internal val KEEP_CORRUPT_FILE = DatabaseErrorHandler { }

    private fun SQLiteDatabase.text(query: String): String? =
        rawQuery(query, null).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun SQLiteDatabase.long(query: String): Long =
        rawQuery(query, null).use { it.moveToFirst(); it.getLong(0) }

    private fun SQLiteDatabase.meta(key: String): String? =
        rawQuery("SELECT value FROM app_meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
}
