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

/**
 * Adds `vente_drafts` for the Dépôt Vente Brouillons, exactly as [MIGRATION_32_33] added
 * `purchase_drafts`.
 *
 * The same warning applies, and for the same reason: both statements are copied verbatim from
 * Room's own generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/34.json`, identity hash
 * 4001e9baa761802df6de9e9945fc3356), with the table-name placeholder substituted. Room validates the resulting schema after the migration runs and throws
 * if the column order, types, nullability or indices differ by so much as a space. **Do not
 * hand-edit these strings** — regenerate the schema and re-copy.
 *
 * The unique index is as load-bearing as the table: it is what makes a second concurrent edit
 * draft for the same vente impossible. Omitting it would pass a smoke test and fail validation.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vente_drafts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`client_id` INTEGER, " +
                "`client_name` TEXT, " +
                "`items_json` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`montant_paye` TEXT NOT NULL, " +
                "`user_name` TEXT NOT NULL, " +
                "`item_count` INTEGER NOT NULL, " +
                "`total` REAL NOT NULL, " +
                "`last_step` TEXT NOT NULL, " +
                "`created_at` TEXT NOT NULL, " +
                "`updated_at` TEXT NOT NULL, " +
                "`source_vente_id` INTEGER, " +
                "`base_fingerprint` TEXT, " +
                "`base_captured_at` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_vente_drafts_source_vente_id` " +
                "ON `vente_drafts` (`source_vente_id`)"
        )
    }
}

/**
 * 34 → 35 — adds `tournee_vente_drafts`.
 *
 * Additive, like the two before it: one CREATE TABLE and one index, no existing table touched, so
 * nothing already on a device can be lost by running it.
 *
 * The same warning applies as above. Both statements are copied from Room's own generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/35.json`) with the table-name
 * placeholder substituted, and Room validates the result after the migration runs — it throws if
 * the column order, types, nullability or indices differ by so much as a space. **Do not hand-edit
 * these strings** — regenerate the schema and re-copy.
 *
 * The index here is not unique, unlike `vente_drafts`'. There is nothing to make unique: the
 * tournée form is create-only, so a tournée can carry any number of unfinished sales at once, and
 * the index exists because every read of this table is "the drafts of one tournée".
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tournee_vente_drafts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tournee_id` INTEGER NOT NULL, " +
                "`client_id` INTEGER, " +
                "`client_name` TEXT, " +
                "`items_json` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`montant_paye` TEXT NOT NULL, " +
                "`item_count` INTEGER NOT NULL, " +
                "`total` REAL NOT NULL, " +
                "`last_step` TEXT NOT NULL, " +
                "`created_at` TEXT NOT NULL, " +
                "`updated_at` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tournee_vente_drafts_tournee_id` " +
                "ON `tournee_vente_drafts` (`tournee_id`)"
        )
    }
}

/**
 * 35 -> 36 - adds `chargement_drafts`.
 *
 * Additive like the three before it: one CREATE TABLE and one index, no existing table touched, so
 * nothing already on a device can be lost by running it.
 *
 * Both statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/36.json`) with the table-name
 * placeholder substituted, and Room validates the result after the migration runs - it throws if
 * the column order, types, nullability or indices differ by so much as a space. **Do not hand-edit
 * these strings** - regenerate the schema and re-copy.
 *
 * The index is unique, and on a nullable column, which is the whole design. It makes "at most one
 * pending edit per product" a fact about the database rather than a convention the UI has to
 * remember, while SQLite's treatment of NULLs as distinct lets any number of ordinary Brouillons
 * coexist alongside.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chargement_drafts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`single_product_id` INTEGER, " +
                "`items_json` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`user_name` TEXT NOT NULL, " +
                "`item_count` INTEGER NOT NULL, " +
                "`created_at` TEXT NOT NULL, " +
                "`updated_at` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_chargement_drafts_single_product_id` " +
                "ON `chargement_drafts` (`single_product_id`)"
        )
    }
}
