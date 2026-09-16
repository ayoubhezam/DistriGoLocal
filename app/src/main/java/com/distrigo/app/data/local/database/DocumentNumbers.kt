package com.distrigo.app.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Gives each new vente, bon d'achat and return the number printed on it: `V-6DED-000124`.
 *
 * ### The format
 *
 * `{type}-{device}-{counter}`:
 *
 *  - **type** — `V` vente, `BA` bon d'achat, `RC` retour client, `RF` retour fournisseur.
 *  - **device** — the first four characters of the id of the phone that created it, in capitals
 *    (see DeviceIdentity). Two phones therefore never print the same number, without talking to each
 *    other or to a server. Two phones sharing a four-character prefix is a 1-in-65,536 chance per
 *    pair; the `uuid`, not the number, is what identifies a document.
 *  - **counter** — one per type, six digits, never reset. It lives in `app_meta` under
 *    `numbering.{table}` and travels with the database.
 *
 * ### Documents that existed before
 *
 * They keep the number they were created with — their id, as MIGRATION_48_49 stored it — because that
 * is what is already on paper. It is stored rather than recomputed from the id, since the same vente
 * will have another local id on another device after a sync. See [numberLabel] for how the two kinds
 * are shown.
 *
 * On first open each counter starts where the table's ids stopped — the highest id AUTOINCREMENT ever
 * handed out, not the highest still present — so a new number never repeats one printed for a
 * document since deleted.
 *
 * ### Why a trigger
 *
 * The number is assigned in the same statement as the insert, inside its transaction, so a sale that
 * rolls back takes its counter increment with it, and every insert path is covered. A row inserted
 * with a `numero` keeps it (a synced document keeps its own). With no `device_id` in `app_meta` no
 * number is assigned, and the document shows `#id` like the old ones.
 *
 * Being numbered is not an edit: `numero` is never compared by the `updated_at` trigger.
 */
internal object DocumentNumberTriggers {

    /** Table to its number prefix. */
    val PREFIXES: Map<String, String> = linkedMapOf(
        "ventes" to "V",
        "purchase_orders" to "BA",
        "retour_client" to "RC",
        "retour_fournisseur" to "RF",
    )

    fun counterKey(table: String): String = "numbering.$table"

    fun triggerName(table: String): String = "trg_${table}_numero"

    fun triggerSql(table: String): String {
        val prefix = PREFIXES.getValue(table)
        val key = counterKey(table)
        return "CREATE TRIGGER `${triggerName(table)}` AFTER INSERT ON `$table` FOR EACH ROW " +
            "WHEN NEW.`numero` IS NULL AND $CURRENT_DEVICE_SQL IS NOT NULL " +
            "BEGIN " +
            "UPDATE `app_meta` SET `value` = CAST(`value` AS INTEGER) + 1 WHERE `key` = '$key'; " +
            "UPDATE `$table` SET `numero` = '$prefix-' || upper(substr($CURRENT_DEVICE_SQL, 1, 4)) || '-' || " +
            "printf('%06d', (SELECT CAST(`value` AS INTEGER) FROM `app_meta` WHERE `key` = '$key')) " +
            "WHERE `id` = NEW.`id`; " +
            "END"
    }

    /**
     * Starts any counter that does not exist yet at the highest id its table has ever used. Existing
     * counters are left alone.
     */
    fun ensureCounters(db: SupportSQLiteDatabase) {
        for (table in PREFIXES.keys) {
            db.execSQL(
                "INSERT OR IGNORE INTO `app_meta` (`key`, `value`) SELECT '${counterKey(table)}', " +
                    "CAST(max(COALESCE((SELECT `seq` FROM sqlite_sequence WHERE `name` = '$table'), 0), " +
                    "COALESCE((SELECT MAX(`id`) FROM `$table`), 0)) AS TEXT)"
            )
        }
    }

    fun install(db: SupportSQLiteDatabase) {
        ensureCounters(db)
        for (table in PREFIXES.keys) {
            replaceIfChanged(db, triggerName(table), triggerSql(table))
        }
    }
}
