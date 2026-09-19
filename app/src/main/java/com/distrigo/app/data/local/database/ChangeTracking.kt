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
 * On a table with a `version` column the same stamp adds one to it, so a row's `version` moves exactly
 * when its `updated_at` does (see [DocumentTriggers] for the rows that have one).
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

    private val NEVER_COMPARED = setOf("id", "uuid", "updated_at", "version", "origin_device_id", "numero")

    fun install(db: SupportSQLiteDatabase) {
        for (table in trackedTables(db)) {
            replaceIfChanged(
                db, triggerName(table),
                triggerSql(table, comparedColumns(db, table), versioned = "version" in columns(db, table)),
            )
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

    fun triggerSql(table: String, compared: List<String>, versioned: Boolean): String {
        val changed = compared.joinToString(" OR ") { "NEW.`$it` IS NOT OLD.`$it`" }
        val version = if (versioned) ", `version` = OLD.`version` + 1" else ""
        return "CREATE TRIGGER `${triggerName(table)}` AFTER UPDATE ON `$table` FOR EACH ROW " +
            "WHEN NEW.`updated_at` = OLD.`updated_at` AND ($changed) " +
            "BEGIN UPDATE `$table` SET `updated_at` = max(OLD.`updated_at` + 1, $NOW_MS)$version " +
            "WHERE `id` = NEW.`id`; END"
    }

    fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }
}

/**
 * Records every hard delete of a business row in `tombstones` (see TombstoneEntity), with the device
 * that deleted it.
 *
 * One `AFTER DELETE` trigger per table [UpdatedAtTriggers] tracks, so the uuid of a deleted vente, of
 * each line replaced by an edit, and of each stock movement removed with its document is kept after
 * the row itself is gone. A soft delete is an UPDATE of `deleted_at` and leaves no tombstone: the row
 * is still there to say so.
 *
 * `INSERT OR IGNORE` because a tombstone must never be the reason a delete fails. A uuid can only be
 * deleted once, so nothing is lost by it.
 */
internal object TombstoneTriggers {

    fun install(db: SupportSQLiteDatabase) {
        for (table in UpdatedAtTriggers.trackedTables(db)) {
            replaceIfChanged(db, triggerName(table), triggerSql(table))
        }
    }

    fun triggerName(table: String): String = "trg_${table}_tombstone"

    fun triggerSql(table: String): String =
        "CREATE TRIGGER `${triggerName(table)}` AFTER DELETE ON `$table` FOR EACH ROW " +
            "BEGIN INSERT OR IGNORE INTO `tombstones` (`table_name`, `row_uuid`, `deleted_at`, `device_id`) " +
            "VALUES ('$table', OLD.`uuid`, $NOW_MS, $CURRENT_DEVICE_SQL); END"
}

/**
 * Makes a change to any part of a document a change to the document.
 *
 * A vente is not one row: it is the `ventes` row, its lines, and the stock movements it produced.
 * Editing a sale replaces the lines and movements and may leave the `ventes` row itself exactly as
 * it was — same total, same note — so its own trigger would never fire, and a sync looking for
 * changed ventes would miss it. The rows that stand on their own therefore carry a `version`, and
 * the tables beneath them bump it.
 *
 * [PARENTS] says which rows belong to which. For each child table, three triggers — after insert,
 * after delete, and after an update that changes something real — stamp the parent's `updated_at`
 * and add one to its `version`, exactly as an edit of the parent row would.
 *
 * `version` therefore counts changed rows, not edits: saving a sale with three lines moves it by
 * several. What it promises is only that it grows whenever any part of the document changes and
 * never otherwise, which is what a sync comparing "the version I started from" needs. Compare it,
 * don't read it.
 *
 * A parent deleted before its children leaves nothing for them to bump, which is harmless: the
 * parent's tombstone already says it is gone.
 */
internal object DocumentTriggers {

    /**
     * Where a child row's parent is. [id] and [condition] are SQL with `{row}` standing for NEW or
     * OLD; [keys] are the child's columns that choose the parent, so an update that moves a row to
     * another parent bumps both.
     */
    class ParentLink(
        val parent: String,
        val keys: List<String>,
        val id: String,
        val condition: String? = null,
    )

    private fun byColumn(parent: String, column: String) = ParentLink(parent, listOf(column), "{row}.`$column`")

    private fun bySource(parent: String, sourceType: String, id: String = "{row}.`source_id`") =
        ParentLink(parent, listOf("source_type", "source_id"), id, "{row}.`source_type` = '$sourceType'")

    val PARENTS: Map<String, List<ParentLink>> = mapOf(
        "vente_items" to listOf(byColumn("ventes", "vente_id")),
        "purchase_order_items" to listOf(byColumn("purchase_orders", "purchase_order_id")),
        "retour_client_items" to listOf(byColumn("retour_client", "retour_id")),
        "retour_fournisseur_items" to listOf(byColumn("retour_fournisseur", "retour_id")),
        "chargement_items" to listOf(byColumn("chargements", "chargement_id")),
        "inventory_items" to listOf(byColumn("inventory_sessions", "session_id")),
        "tournee_clients" to listOf(byColumn("tournees", "tournee_id")),
        "tournee_secteurs" to listOf(byColumn("tournees", "tournee_id")),
        "policy_tiers" to listOf(byColumn("target_policies", "policy_id")),
        "product_images" to listOf(byColumn("products", "product_id")),
        "product_barcodes" to listOf(byColumn("products", "product_id")),
        // A movement names its document by type; an inventory movement names a line of the session.
        "stock_movements" to listOf(
            bySource("ventes", "vente"),
            bySource("purchase_orders", "purchase_order"),
            bySource("retour_client", "retour_client"),
            bySource("retour_fournisseur", "retour_fournisseur"),
            bySource("pertes", "perte"),
            bySource(
                "inventory_sessions", "inventory_item",
                id = "(SELECT `session_id` FROM `inventory_items` WHERE `id` = {row}.`source_id`)",
            ),
        ),
        // A perte recorded by a return is part of that return. Pertes also stand on their own.
        "pertes" to listOf(
            bySource("retour_client", "retour_client"),
            bySource("retour_fournisseur", "retour_fournisseur"),
        ),
    )

    fun install(db: SupportSQLiteDatabase) {
        for ((child, links) in PARENTS) {
            val compared = UpdatedAtTriggers.comparedColumns(db, child)
            for ((name, sql) in triggers(child, links, compared)) {
                replaceIfChanged(db, name, sql)
            }
        }
    }

    fun triggerName(child: String, event: String): String = "trg_${child}_parent_$event"

    fun triggers(child: String, links: List<ParentLink>, compared: List<String>): List<Pair<String, String>> {
        fun bumps(row: String, extra: (ParentLink) -> String? = { null }) =
            links.joinToString(" ") { link ->
                val where = listOfNotNull(
                    "`id` = ${link.id.replace("{row}", row)}",
                    link.condition?.replace("{row}", row),
                    extra(link),
                ).joinToString(" AND ")
                "UPDATE `${link.parent}` SET `updated_at` = max(`updated_at` + 1, $NOW_MS), " +
                    "`version` = `version` + 1 WHERE $where;"
            }

        val changed = compared.joinToString(" OR ") { "NEW.`$it` IS NOT OLD.`$it`" }
        // After an update, the old parent too — but only if the row moved to another one.
        val moved = { link: ParentLink -> "(" + link.keys.joinToString(" OR ") { "NEW.`$it` IS NOT OLD.`$it`" } + ")" }

        return listOf(
            triggerName(child, "insert") to
                "CREATE TRIGGER `${triggerName(child, "insert")}` AFTER INSERT ON `$child` FOR EACH ROW " +
                "BEGIN ${bumps("NEW")} END",
            triggerName(child, "update") to
                "CREATE TRIGGER `${triggerName(child, "update")}` AFTER UPDATE ON `$child` FOR EACH ROW " +
                "WHEN $changed BEGIN ${bumps("NEW")} ${bumps("OLD", moved)} END",
            triggerName(child, "delete") to
                "CREATE TRIGGER `${triggerName(child, "delete")}` AFTER DELETE ON `$child` FOR EACH ROW " +
                "BEGIN ${bumps("OLD")} END",
        )
    }
}

/** Milliseconds since the Unix epoch, UTC. `julianday` exists on every SQLite Android ships. */
private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

internal fun storedTriggerSql(db: SupportSQLiteDatabase, name: String): String? =
    db.query("SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = ?", arrayOf(name))
        .use { c -> if (c.moveToFirst()) c.getString(0) else null }

/** SQLite stores a trigger's CREATE statement as written, so an unchanged one compares equal. */
internal fun replaceIfChanged(db: SupportSQLiteDatabase, name: String, sql: String) {
    if (storedTriggerSql(db, name) == sql) return
    db.execSQL("DROP TRIGGER IF EXISTS `$name`")
    db.execSQL(sql)
}

/**
 * Installs the [UpdatedAtTriggers], [TombstoneTriggers], [DocumentTriggers], [StockLedgerTriggers],
 * [OriginTriggers] and [DocumentNumberTriggers] each time the database opens, in one transaction. The app's builder and the tests use it.
 */
internal fun RoomDatabase.Builder<AppDatabase>.withChangeTracking(): RoomDatabase.Builder<AppDatabase> =
    addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                UpdatedAtTriggers.install(db)
                TombstoneTriggers.install(db)
                DocumentTriggers.install(db)
                StockLedgerTriggers.install(db)
                OriginTriggers.install(db)
                DocumentNumberTriggers.install(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    })
