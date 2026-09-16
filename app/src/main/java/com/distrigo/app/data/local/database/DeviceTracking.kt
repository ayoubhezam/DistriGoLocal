package com.distrigo.app.data.local.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Stamps which device created each standalone row.
 *
 * Every table with a `origin_device_id` column — the 24 that carry a `version` — gets an `AFTER INSERT`
 * trigger that fills it from `app_meta.device_id` when the insert left it NULL. Lines have no column
 * of their own: a vente line was created wherever its vente was.
 *
 * An insert that names an origin keeps it, which is what a sync writing another device's row will
 * need. The stamp is not an edit: `origin_device_id` is never compared by the `updated_at` trigger,
 * so setting it neither moves `updated_at` nor bumps `version`.
 *
 * Rows that existed before version 48 keep a NULL origin. Nothing recorded where they were made,
 * and guessing "this phone" would be wrong for a database restored from another one.
 *
 * With no `device_id` in `app_meta` — a database opened without [withDeviceIdentity] — the subquery is
 * NULL and so is the origin, which is honest rather than wrong.
 */
internal object OriginTriggers {

    fun triggerName(table: String): String = "trg_${table}_origin"

    fun triggerSql(table: String): String =
        "CREATE TRIGGER `${triggerName(table)}` AFTER INSERT ON `$table` FOR EACH ROW " +
            "WHEN NEW.`origin_device_id` IS NULL " +
            "BEGIN UPDATE `$table` SET `origin_device_id` = $CURRENT_DEVICE_SQL WHERE `id` = NEW.`id`; END"

    fun originTables(db: SupportSQLiteDatabase): List<String> =
        UpdatedAtTriggers.trackedTables(db).filter { "origin_device_id" in UpdatedAtTriggers.columns(db, it) }

    fun install(db: SupportSQLiteDatabase) {
        for (table in originTables(db)) {
            replaceIfChanged(db, triggerName(table), triggerSql(table))
        }
    }
}

/** The device that has this database open, as SQL. */
internal const val CURRENT_DEVICE_SQL = "(SELECT `value` FROM `app_meta` WHERE `key` = 'device_id')"

/** A version-4 UUID, as SQL; evaluated once per row. */
internal const val UUID_V4_SQL =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', 1 + (random() & 3), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "lower(hex(randomblob(6)))"

/**
 * Records, each time the database opens, which installation opened it — and gives the database its
 * own `database_id` the first time.
 *
 * [deviceId] is asked for inside `onOpen`, which Room runs on a background thread, because reading
 * DeviceIdentity's file is I/O. Added after [withChangeTracking] so the triggers that read
 * `app_meta.device_id` exist before anything is inserted.
 */
internal fun RoomDatabase.Builder<AppDatabase>.withDeviceIdentity(
    deviceId: () -> String,
): RoomDatabase.Builder<AppDatabase> =
    addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO `app_meta` (`key`, `value`) VALUES ('database_id', $UUID_V4_SQL)")
            db.execSQL(
                "INSERT OR REPLACE INTO `app_meta` (`key`, `value`) VALUES ('device_id', ?)",
                arrayOf(deviceId()),
            )
        }
    })
