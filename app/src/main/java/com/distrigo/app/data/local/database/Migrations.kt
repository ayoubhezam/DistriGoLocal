package com.distrigo.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The first real migration in this project.
 *
 * Every version bump before 33 relied on `fallbackToDestructiveMigration()`, which wipes the
 * database on upgrade. Room resolves a registered migration path *before* it considers destructive
 * fallback, so registering this one makes 32 → 33 non-destructive without removing the fallback —
 * which is deliberately kept for pre-32 installs, where dropping it would turn today's
 * already-accepted wipe into a hard crash on open.
 *
 * Both statements below are copied verbatim from Room's own generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/33.json`, identity hash
 * f511883dc28ba8597b3707a6dba7a9bb), with `${'$'}{TABLE_NAME}` substituted. They have to match
 * byte-for-byte: Room validates the resulting schema after the migration runs and throws if the
 * column order, types, nullability or indices differ from what it expects. **Do not hand-edit
 * these strings** — regenerate the schema and re-copy.
 *
 * The unique index is as load-bearing as the table: it is what makes a second concurrent edit
 * draft for the same bon impossible. Omitting it would pass a smoke test and fail validation.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_drafts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`supplier_id` INTEGER, " +
                "`supplier_name` TEXT, " +
                "`items_json` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`montant_paye` TEXT NOT NULL, " +
                "`item_count` INTEGER NOT NULL, " +
                "`total` REAL NOT NULL, " +
                "`last_step` TEXT NOT NULL, " +
                "`created_at` TEXT NOT NULL, " +
                "`updated_at` TEXT NOT NULL, " +
                "`source_order_id` INTEGER, " +
                "`base_fingerprint` TEXT, " +
                "`base_captured_at` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_drafts_source_order_id` " +
                "ON `purchase_drafts` (`source_order_id`)"
        )
    }
}
