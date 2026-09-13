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

/**
 * 36 -> 37 - the first migration that adds nothing and indexes everything.
 *
 * Until now this database had exactly four indexes, all of them on draft tables, all of them
 * created because a uniqueness rule needed enforcing. Nothing else was indexed, so every lookup
 * that was not by primary key was a full table scan: `getItemsForVente` scanned all of
 * `vente_items`, `getVentesForClient` scanned all of `ventes`, `deleteBySource` scanned all of
 * `stock_movements` on every edit of a bon, and the keyset cursors that make the client and
 * supplier ledgers paginate scanned the table once per page. That is invisible at a few thousand
 * rows and superlinear after that.
 *
 * The thirty indexes below are derived from the DAOs rather than from a rule of thumb: each one
 * covers a column that some `@Query` actually filters on, in the order that query filters and then
 * orders. Columns that are only ever *sorted* on a full-table read get no index, because the read
 * is O(n) whether or not the sort is - the point is to stop scanning, not to shave a sort. Config
 * tables bounded by what a user curates (categories, marques, perte_types, charge_subtypes,
 * secteurs) get none either, for the same reason: a scan of eighty rows costs nothing, and an index
 * on it is maintenance with no reader.
 *
 * `products.barcode` is deliberately *not* indexed. Barcode search happens in Kotlin over the
 * in-memory catalogue today; there is no SQL query to serve, so an index would sit unused. It
 * belongs with the change that moves that search into SQLite, not here.
 *
 * Unlike 32->33 through 35->36 this migration touches existing tables - but only by adding indexes
 * to them, so no column, row or table can be lost by running it. On a large database it is the
 * slowest migration so far: SQLite builds each index by sorting the table once. That is a one-time
 * cost paid at upgrade, and `IF NOT EXISTS` makes every statement safe to re-run.
 *
 * The names and column lists are Room's own, copied from the generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/37.json`). Room validates the
 * result after the migration runs and throws if an index it expects is missing or shaped
 * differently, so these have to match exactly. **Do not hand-edit these strings** - change the
 * `@Entity(indices = ...)` declaration, rebuild, and re-copy from the schema.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The read paths behind every ledger, detail screen and report
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_ventes_client_id_created_at` ON `ventes` (`client_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_ventes_tournee_id` ON `ventes` (`tournee_id`)",
            "CREATE INDEX IF NOT EXISTS `index_ventes_created_at` ON `ventes` (`created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_ventes_source_created_at` ON `ventes` (`source`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_vente_items_vente_id` ON `vente_items` (`vente_id`)",
            "CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplier_id_created_at` ON `purchase_orders` (`supplier_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_purchase_order_items_purchase_order_id` ON `purchase_order_items` (`purchase_order_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_payments_client_id_created_at` ON `client_payments` (`client_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_client_payments_created_at` ON `client_payments` (`created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_supplier_payments_supplier_id_created_at` ON `supplier_payments` (`supplier_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_price_history_product_id_created_at` ON `price_history` (`product_id`, `created_at`)",
        ).forEach(db::execSQL)

        // Stock movements - the fastest-growing table, and the one edits delete from by source
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_product_id_created_at` ON `stock_movements` (`product_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_source_type_source_id` ON `stock_movements` (`source_type`, `source_id`)",
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_created_at` ON `stock_movements` (`created_at`)",
        ).forEach(db::execSQL)

        // Tournees, inventaire, pertes, charges, chargements, retours
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_products_supplier_id` ON `products` (`supplier_id`)",
            "CREATE INDEX IF NOT EXISTS `index_tournees_status` ON `tournees` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_tournee_clients_tournee_id_order_index` ON `tournee_clients` (`tournee_id`, `order_index`)",
            "CREATE INDEX IF NOT EXISTS `index_inventory_sessions_status` ON `inventory_sessions` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_inventory_items_session_id_created_at` ON `inventory_items` (`session_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_inventory_items_session_id_product_id` ON `inventory_items` (`session_id`, `product_id`)",
            "CREATE INDEX IF NOT EXISTS `index_pertes_type_id_date_time` ON `pertes` (`type_id`, `date_time`)",
            "CREATE INDEX IF NOT EXISTS `index_pertes_source_type_source_id` ON `pertes` (`source_type`, `source_id`)",
            "CREATE INDEX IF NOT EXISTS `index_charges_subtype_id_date_time` ON `charges` (`subtype_id`, `date_time`)",
            "CREATE INDEX IF NOT EXISTS `index_chargements_session_id` ON `chargements` (`session_id`)",
            "CREATE INDEX IF NOT EXISTS `index_chargement_items_chargement_id` ON `chargement_items` (`chargement_id`)",
            "CREATE INDEX IF NOT EXISTS `index_chargement_sessions_session_date` ON `chargement_sessions` (`session_date`)",
            "CREATE INDEX IF NOT EXISTS `index_retour_client_client_id_date` ON `retour_client` (`client_id`, `date`)",
            "CREATE INDEX IF NOT EXISTS `index_retour_client_items_retour_id` ON `retour_client_items` (`retour_id`)",
            "CREATE INDEX IF NOT EXISTS `index_retour_fournisseur_supplier_id_date` ON `retour_fournisseur` (`supplier_id`, `date`)",
            "CREATE INDEX IF NOT EXISTS `index_retour_fournisseur_items_retour_id` ON `retour_fournisseur_items` (`retour_id`)",
        ).forEach(db::execSQL)
    }
}

/**
 * 37 -> 38 - adds `product_images`, the table behind the product photo gallery.
 *
 * Two statements create the table and its index; the third is the only data migration in this
 * project so far, and it is deliberately trivial: every product that already has a photo gets that
 * photo as its gallery's first and only entry.
 *
 *     INSERT INTO product_images (...) SELECT id, image_uri, 0, ... FROM products WHERE ...
 *
 * That runs entirely inside SQLite - no files are read, nothing is decoded, nothing can half-finish
 * the way moving bytes onto the filesystem could. It is safe in a Migration for exactly the reason
 * ImageBackfill is not.
 *
 * `products.image_uri` is left alone, and stays the cover. Everything that wants one picture - the
 * product rows, the cart lines, the pickers, and the denormalised snapshots on ventes,
 * purchase_orders, inventory_items and pertes - keeps reading it and is untouched by this change.
 * The repository keeps it equal to position 0 from here on.
 *
 * The WHERE clause copies whatever the column holds, `img:` reference or legacy `data:` payload.
 * On a device whose backfill has not run yet that means a payload lands in `image_ref` - which is
 * why `product_images` was added to ImageBackfill's walk. Content addressing makes the two converge
 * on the same `img:` reference regardless of which is converted first.
 *
 * The statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/38.json`) with the table-name
 * placeholder substituted. **Do not hand-edit these strings** - change the entity, rebuild, and
 * re-copy.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `product_images` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`product_id` INTEGER NOT NULL, " +
                "`image_ref` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`created_at` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_product_images_product_id_position` " +
                "ON `product_images` (`product_id`, `position`)"
        )
        // Seed each existing photo as its product's cover. TRIM guards the empty string, which is
        // not the same as NULL and would otherwise become a gallery entry pointing at nothing.
        db.execSQL(
            "INSERT INTO `product_images` (`product_id`, `image_ref`, `position`, `created_at`) " +
                "SELECT `id`, `image_uri`, 0, '1970-01-01T00:00:00Z' FROM `products` " +
                "WHERE `image_uri` IS NOT NULL AND TRIM(`image_uri`) != ''"
        )
    }
}


/**
 * 38 -> 39 - adds `tournee_secteurs`, the join table behind a tournée's secteurs.
 *
 * Purely additive: one new table and its index, no data migration. Existing tournées simply have no
 * rows here, which reads back as an empty secteur list and leaves their card and top bar showing
 * the commune alone, exactly as before.
 *
 * The statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/39.json`) with the table-name
 * placeholder substituted. **Do not hand-edit these strings** - change the entity, rebuild, and
 * re-copy.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tournee_secteurs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tournee_id` INTEGER NOT NULL, " +
                "`secteur_id` INTEGER NOT NULL, " +
                "`secteur_name` TEXT NOT NULL, " +
                "`order_index` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tournee_secteurs_tournee_id_order_index` " +
                "ON `tournee_secteurs` (`tournee_id`, `order_index`)"
        )
    }
}

/**
 * 39 -> 40 - gives "Effectué par" somewhere to live on a sale.
 *
 * Two `ALTER TABLE ... ADD COLUMN`s, no data migration.
 *
 * `ventes.user_name` closes a gap rather than adding a feature: Dépôt Vente has always asked who
 * made the sale, but the answer only ever reached `stock_movements.user_name`, so the vente could
 * not report it and the receipt had nothing to print. Existing rows get NULL, which reads as "not
 * recorded" and prints as "-" - correct, because for those sales it genuinely was not.
 *
 * `tournee_vente_drafts.user_name` is the same field on the van-sale form, which now asks for it
 * too. NOT NULL with a `''` default rather than nullable, matching `note` beside it: that table
 * stores the form's raw strings, and an empty box is an empty string there, never NULL.
 *
 * The statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/40.json`). **Do not hand-edit
 * these strings** - change the entity, rebuild, and re-copy.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `ventes` ADD COLUMN `user_name` TEXT")
        db.execSQL(
            "ALTER TABLE `tournee_vente_drafts` ADD COLUMN `user_name` TEXT NOT NULL DEFAULT ''"
        )
    }
}

/**
 * 40 -> 41 - repairs every stored supplier and client balance. No schema change.
 *
 * Balances were recomputed by four private copies of one formula, and the copies disagreed: the
 * two in ProductRepository ignored returns, the two in the Retour repositories subtracted them. A
 * return lowered the balance, and the next sale, purchase or payment for that party recomputed it
 * without the return and quietly put the amount back. Real devices were already showing it.
 *
 * The formula now has one home per party, SupplierDao.recomputeBalance and
 * ClientDao.recomputeBalance, which fixes each balance the next time its party is touched. This
 * fixes the ones already stored, so no party has to be touched first.
 *
 * Recomputing every row is safe because `balance` holds nothing a user typed that is not also
 * kept elsewhere: the supplier form's "Solde initial" writes the same value to `initial_balance`,
 * which the formula keeps, and no client form sends a balance at all.
 *
 * The SQL is inlined rather than shared with the DAOs on purpose. A migration records what was
 * done to the data at one version; if the formula changes later, this must not change with it.
 * Pure SQL, correlated on each row's id, run once inside Room's migration transaction.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE suppliers SET balance =
                  initial_balance
                + (SELECT COALESCE(SUM(po.total), 0.0) - COALESCE(SUM(po.montant_paye), 0.0)
                     FROM purchase_orders po    WHERE po.supplier_id = suppliers.id)
                - (SELECT COALESCE(SUM(sp.amount), 0.0)
                     FROM supplier_payments sp  WHERE sp.supplier_id = suppliers.id)
                - (SELECT COALESCE(SUM(rf.total), 0.0)
                     FROM retour_fournisseur rf WHERE rf.supplier_id = suppliers.id)
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE clients SET balance =
                  (SELECT COALESCE(SUM(v.total), 0.0) - COALESCE(SUM(v.montant_paye), 0.0)
                     FROM ventes v           WHERE v.client_id = clients.id)
                - (SELECT COALESCE(SUM(cp.amount), 0.0)
                     FROM client_payments cp WHERE cp.client_id = clients.id)
                - (SELECT COALESCE(SUM(rc.total), 0.0)
                     FROM retour_client rc   WHERE rc.client_id = clients.id)
            """.trimIndent()
        )
    }
}
