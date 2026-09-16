package com.distrigo.app.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Makes `products.stock` and `products.camion_stock` what the ledger says they are, always.
 *
 * ### The ledger
 *
 * Stock is not a number anyone types. It is the sum of what happened to a product:
 *
 *  - **`stock_movements`** — every sale, purchase received, return, perte and inventory adjustment,
 *    `entree` adding and `sortie` subtracting. Their sum is the product's total stock, dépôt and
 *    camion together; the ones with `emplacement = 'camion'` are the camion's share of it.
 *  - **`chargement_items`** — transfers between the dépôt and the camion. They move stock from one
 *    place to the other without changing the total, so they count towards `camion_stock` only
 *    (`vers_camion` adds, `vers_depot` subtracts). They are deliberately not stock movements: a
 *    chargement is an internal split, not a movement of the global stock, and the Mouvements screen
 *    has never listed them.
 *
 * So `stock` = Σ movements and `camion_stock` = Σ camion movements + Σ transfers, and the two columns
 * are caches of those sums, exactly as `clients.balance` is a cache of the client's ledger.
 *
 * ### Why triggers keep them
 *
 * Until now every write path did its own arithmetic on a product it had read — `stock + quantity`,
 * `camion_stock - quantity` — and wrote the whole row back. The movement was recorded beside the
 * change, not as the cause of it, so the two could drift, and a product row read before a sale and
 * written after it put the sale's stock back.
 *
 * Here the movement is the only thing code writes. After any insert, update or delete of a movement
 * or a transfer line, a trigger recomputes that product from the ledger. A last trigger guards the
 * columns themselves: if a write leaves `stock` or `camion_stock` different from the ledger — a whole
 * row saved from a stale copy, a direct assignment — it puts the ledger's value back. Nothing outside
 * this file can make the caches wrong, and a sync will never need to carry them: it carries the
 * movements and each device recomputes.
 *
 * A recompute reads one product's movements through `index_stock_movements_product_id_created_at`
 * and its transfer lines through `index_chargement_items_product_id`.
 *
 * Installed with the other triggers when the database opens (see ChangeTracking.kt).
 */
internal object StockLedgerTriggers {

    private fun signed(entree: String) =
        "CASE WHEN `direction` = '$entree' THEN `quantity` ELSE -`quantity` END"

    /** The product's total stock according to its movements. */
    fun totalSql(productId: String) =
        "(SELECT COALESCE(SUM(${signed("entree")}), 0.0) FROM `stock_movements` WHERE `product_id` = $productId)"

    /** The camion's share: camion movements plus transfers. */
    fun camionSql(productId: String) =
        "((SELECT COALESCE(SUM(${signed("entree")}), 0.0) FROM `stock_movements` " +
            "WHERE `product_id` = $productId AND `emplacement` = 'camion') + " +
            "(SELECT COALESCE(SUM(${signed("vers_camion")}), 0.0) FROM `chargement_items` " +
            "WHERE `product_id` = $productId))"

    private fun recompute(productId: String, extra: String = "") =
        "UPDATE `products` SET `stock` = ${totalSql(productId)}, `camion_stock` = ${camionSql(productId)} " +
            "WHERE `id` = $productId$extra;"

    fun triggers(): List<Pair<String, String>> {
        val ledgerTables = listOf("stock_movements", "chargement_items")
        val onLedger = ledgerTables.flatMap { table ->
            listOf(
                "trg_${table}_stock_insert" to
                    "CREATE TRIGGER `trg_${table}_stock_insert` AFTER INSERT ON `$table` FOR EACH ROW " +
                    "BEGIN ${recompute("NEW.`product_id`")} END",
                "trg_${table}_stock_update" to
                    "CREATE TRIGGER `trg_${table}_stock_update` AFTER UPDATE ON `$table` FOR EACH ROW " +
                    "BEGIN ${recompute("NEW.`product_id`")} " +
                    "${recompute("OLD.`product_id`", " AND OLD.`product_id` IS NOT NEW.`product_id`")} END",
                "trg_${table}_stock_delete" to
                    "CREATE TRIGGER `trg_${table}_stock_delete` AFTER DELETE ON `$table` FOR EACH ROW " +
                    "BEGIN ${recompute("OLD.`product_id`")} END",
            )
        }
        val drifted = "NEW.`stock` IS NOT ${totalSql("NEW.`id`")} OR NEW.`camion_stock` IS NOT ${camionSql("NEW.`id`")}"
        val guards = listOf(
            "trg_products_stock_insert" to
                "CREATE TRIGGER `trg_products_stock_insert` AFTER INSERT ON `products` FOR EACH ROW " +
                "WHEN $drifted BEGIN ${recompute("NEW.`id`")} END",
            "trg_products_stock_update" to
                "CREATE TRIGGER `trg_products_stock_update` AFTER UPDATE OF `stock`, `camion_stock` ON `products` " +
                "FOR EACH ROW WHEN $drifted BEGIN ${recompute("NEW.`id`")} END",
        )
        return onLedger + guards
    }

    fun install(db: SupportSQLiteDatabase) {
        for ((name, sql) in triggers()) {
            replaceIfChanged(db, name, sql)
        }
    }
}
