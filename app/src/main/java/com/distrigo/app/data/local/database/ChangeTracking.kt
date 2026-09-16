package com.distrigo.app.data.local.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps every business row's `updated_at` true, whatever code path wrote to it.
 *
 * ### Why a trigger, not the repositories
 *
 * Rows change through some forty `@Update` calls on copied entities and a dozen `@Query` UPDATEs,
 * and many of those writes change nothing a user did: every sale rewrites the product row to move
 * `stock`, every payment runs `recomputeBalance` over the client. Stamping in Kotlin would mean
 * remembering, at each of those sites, whether this write is a real edit or a cache refresh — and a
 * wrong guess is not cosmetic. A sync that resolves conflicts by the newest `updated_at` would let a
 * sale on one device overwrite a client renamed on another.
 *
 * One trigger per table decides by looking at the values instead. It fires after an UPDATE and
 * stamps the row only when a column that means something actually changed:
 *
 *  - **Derived columns are ignored** ([DERIVED_COLUMNS]): stock and balances are caches rebuilt from
 *    other rows, and a sync will recompute them rather than carry them.
 *  - **An explicit `updated_at` wins.** If the write itself changed `updated_at`, the trigger stays
 *    out of it, which is what a sync applying another device's row will need.
 *  - **It never goes backwards.** The stamp is the later of now and the old value plus one
 *    millisecond, so a phone whose clock is set back still produces an increasing value.
 *
 * Inserts are not the trigger's business: an entity is built with `updated_at` set to its creation
 * time, and nothing inserts rows with raw SQL outside the migrations.
 *
 * ### Why built on every open
 *
 * Room creates tables and indices, but not triggers, and does not validate them. Rather than copy
 * the trigger into `onCreate`, the destructive path and every future migration — and forget to
 * update it when a column is added — [UpdatedAtTriggers.install] builds each definition from the
 * table's columns as they are now, compares it with the one stored, and only rewrites a trigger that
 * differs. A new column is covered the first time the app opens after the migration that added it.
 */
internal object UpdatedAtTriggers {

    /** Columns holding a cache recomputed from other rows. Changing only these is not an edit. */
    val DERIVED_COLUMNS: Map<String, Set<String>> = mapOf(
        "products" to setOf("stock", "camion_stock"),
        "clients" to setOf("balance"),
        "suppliers" to setOf("balance"),
    )

    private val NEVER_COMPARED = setOf("id", "uuid", "updated_at")

    /** Milliseconds since the Unix epoch, UTC. `julianday` exists on every SQLite Android ships. */
    private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    fun install(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            for (table in trackedTables(db)) {
                val name = triggerName(table)
                val wanted = triggerSql(table, comparedColumns(db, table))
                if (storedTriggerSql(db, name) != wanted) {
                    db.execSQL("DROP TRIGGER IF EXISTS `$name`")
                    db.execSQL(wanted)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun triggerName(table: String): String = "trg_${table}_updated_at"

    /** The business tables: those carrying both a `uuid` and an `updated_at`. Drafts have no `uuid`. */
    fun trackedTables(db: SupportSQLiteDatabase): List<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name"
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            .filter { table -> columns(db, table).containsAll(listOf("uuid", "updated_at")) }

    fun comparedColumns(db: SupportSQLiteDatabase, table: String): List<String> =
        columns(db, table) - NEVER_COMPARED - DERIVED_COLUMNS[table].orEmpty()

    fun triggerSql(table: String, compared: List<String>): String {
        val changed = compared.joinToString(" OR ") { "NEW.`$it` IS NOT OLD.`$it`" }
        return "CREATE TRIGGER `${triggerName(table)}` AFTER UPDATE ON `$table` FOR EACH ROW " +
            "WHEN NEW.`updated_at` = OLD.`updated_at` AND ($changed) " +
            "BEGIN UPDATE `$table` SET `updated_at` = max(OLD.`updated_at` + 1, $NOW_MS) " +
            "WHERE `id` = NEW.`id`; END"
    }

    fun storedTriggerSql(db: SupportSQLiteDatabase, name: String): String? =
        db.query("SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = ?", arrayOf(name))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }
}

/** Installs [UpdatedAtTriggers] each time the database opens. The app's builder and the tests use it. */
internal fun RoomDatabase.Builder<AppDatabase>.withChangeTracking(): RoomDatabase.Builder<AppDatabase> =
    addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            UpdatedAtTriggers.install(db)
        }
    })
