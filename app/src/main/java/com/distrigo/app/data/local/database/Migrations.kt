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

/**
 * The charge month queries test `date_time` as a half-open range instead of
 * `substr(date_time, 1, 7)`, which wrapped the column in a function and so could use no index at
 * all. This index serves the per-type month totals; the per-subtype ones already had
 * `index_charges_subtype_id_date_time` from [MIGRATION_36_37].
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_charges_type_id_date_time` ON `charges` (`type_id`, `date_time`)"
        )
    }
}

/**
 * 42 -> 43 - gives every business row a stable identity: a `uuid` column, filled and unique.
 *
 * The Int `id` stays the key everything in the app uses. It is only unique on one device, though,
 * so a backup merge or a sync could not tell a vente from another device's vente with the same id.
 * The UUID can. Nothing reads it yet; new rows get theirs from the entity (see `newRowUuid`).
 *
 * Three statements per table:
 *
 *  1. `ADD COLUMN ... NOT NULL DEFAULT ''`. SQLite cannot add a NOT NULL column without a default,
 *     and the entities declare the same `''` so a fresh install and a migrated one have the same
 *     schema. The default is never what a row keeps.
 *  2. An UPDATE that gives every existing row its own random version-4 UUID, built in SQL from
 *     `randomblob`, which SQLite evaluates once per row. `random() & 3` picks the variant digit
 *     without `abs()`, which overflows on the smallest 64-bit integer.
 *  3. The unique index, created only after the backfill, since every row shared `''` until then.
 *     Room's own name for it, `index_<table>_uuid`, which Room validates after the migration.
 *
 * The four draft tables are left out on purpose: a draft never leaves the device it was typed on.
 *
 * The table list is written out here rather than derived from the entities: this migration records
 * what version 43 was, and must not grow if a table is added later.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf(
            // Catalogue, parties, config
            "products", "product_images", "categories", "sous_categories", "marques",
            "price_history", "clients", "suppliers", "secteurs",
            "charge_types", "charge_subtypes", "perte_types", "target_policies", "policy_tiers",
            // Sales and purchases
            "ventes", "vente_items", "client_payments", "retour_client", "retour_client_items",
            "purchase_orders", "purchase_order_items", "supplier_payments",
            "retour_fournisseur", "retour_fournisseur_items",
            // Stock, tournées, expenses
            "stock_movements", "chargement_sessions", "chargements", "chargement_items",
            "inventory_sessions", "inventory_items", "pertes",
            "tournees", "tournee_clients", "tournee_secteurs", "charges",
        )
        val uuidV4 =
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', 1 + (random() & 3), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"
        for (table in tables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$table` SET `uuid` = $uuidV4")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
        }
    }
}

/**
 * 43 -> 44 - `updated_at` on every business table, and `created_at` on the fourteen that had none.
 *
 * **`updated_at`** is `INTEGER NOT NULL`, milliseconds since the epoch in UTC, on the same 35 tables
 * that carry a `uuid`. Every existing row gets the moment this migration ran: nothing recorded when
 * rows last changed, and "as of the upgrade" is the one statement about them that is true. From here
 * on new rows take their creation time from the entity, and edits are stamped by the triggers in
 * ChangeTracking.kt, which the app installs when the database opens rather than here.
 *
 * **`created_at`** is `TEXT`, ISO-8601 like the column of that name on every other table. Where a
 * row belongs to a parent that recorded its own creation, it takes the parent's:
 *
 *  - vente, purchase order, chargement and return lines from their document,
 *  - a tournée's clients and secteurs from the tournée, a policy's tiers from the policy,
 *  - an inventory session from its own `started_at`.
 *
 * The rest — products, clients, categories, sous-catégories, marques — never recorded when they were
 * created, and get `1970-01-01T00:00:00Z`, the same "unknown" that MIGRATION_37_38 gave the photos
 * it seeded. So does a line whose parent is gone. A date made up at migration time would read as
 * real.
 *
 * Both columns are added with a default only because SQLite requires one for a NOT NULL column; the
 * entities declare the same defaults, and every row is backfilled before anything reads it.
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf(
            "products", "product_images", "categories", "sous_categories", "marques",
            "price_history", "clients", "suppliers", "secteurs",
            "charge_types", "charge_subtypes", "perte_types", "target_policies", "policy_tiers",
            "ventes", "vente_items", "client_payments", "retour_client", "retour_client_items",
            "purchase_orders", "purchase_order_items", "supplier_payments",
            "retour_fournisseur", "retour_fournisseur_items",
            "stock_movements", "chargement_sessions", "chargements", "chargement_items",
            "inventory_sessions", "inventory_items", "pertes",
            "tournees", "tournee_clients", "tournee_secteurs", "charges",
        )
        val migratedAt = System.currentTimeMillis()
        for (table in tables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET `updated_at` = $migratedAt")
        }

        val unknown = "'1970-01-01T00:00:00Z'"
        // table to the expression giving each row its creation time
        val createdAt = linkedMapOf(
            "vente_items" to "(SELECT p.created_at FROM ventes p WHERE p.id = vente_items.vente_id)",
            "purchase_order_items" to
                "(SELECT p.created_at FROM purchase_orders p WHERE p.id = purchase_order_items.purchase_order_id)",
            "chargement_items" to
                "(SELECT p.created_at FROM chargements p WHERE p.id = chargement_items.chargement_id)",
            "retour_client_items" to
                "(SELECT p.created_at FROM retour_client p WHERE p.id = retour_client_items.retour_id)",
            "retour_fournisseur_items" to
                "(SELECT p.created_at FROM retour_fournisseur p WHERE p.id = retour_fournisseur_items.retour_id)",
            "tournee_clients" to "(SELECT p.created_at FROM tournees p WHERE p.id = tournee_clients.tournee_id)",
            "tournee_secteurs" to "(SELECT p.created_at FROM tournees p WHERE p.id = tournee_secteurs.tournee_id)",
            "policy_tiers" to "(SELECT p.created_at FROM target_policies p WHERE p.id = policy_tiers.policy_id)",
            "inventory_sessions" to "started_at",
            "products" to unknown,
            "clients" to unknown,
            "categories" to unknown,
            "sous_categories" to unknown,
            "marques" to unknown,
        )
        for ((table, value) in createdAt) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `created_at` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$table` SET `created_at` = COALESCE($value, $unknown)")
        }
    }
}

/**
 * 44 -> 45 - deleting stops destroying: soft delete for master data, tombstones for the rest.
 *
 * **Soft delete.** The nine tables the app lets a user delete from directly — products, clients,
 * suppliers, categories, sous-catégories, marques, charge types and subtypes, perte types — get a
 * nullable `deleted_at`. Their DAOs now set it instead of deleting the row, and every read of those
 * tables filters it out, so a deleted row looks exactly as absent as it did when it was really gone:
 * out of every list and picker, null from every lookup by id, and no name in the vente joins. What
 * changes is that the row, and every sale, purchase and movement that points at it, is still there
 * for a restore or a sync. Existing rows get NULL; nothing deleted before today can be brought back.
 *
 * Setting `deleted_at` is a real change, so the `updated_at` trigger stamps it like any edit.
 *
 * **Tombstones.** Everything else is still deleted for real — documents, their lines, payments,
 * movements. `tombstones` keeps the table and `uuid` of each such row, written by an `AFTER DELETE`
 * trigger installed with the others in ChangeTracking.kt. The table starts empty: rows deleted
 * before this version left no uuid behind to record.
 *
 * The CREATE statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/45.json`). **Do not hand-edit
 * them** - change the entity, rebuild, and re-copy.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "products", "clients", "suppliers", "categories", "sous_categories", "marques",
            "charge_types", "charge_subtypes", "perte_types",
        ).forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `deleted_at` INTEGER")
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tombstones` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`table_name` TEXT NOT NULL, " +
                "`row_uuid` TEXT NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tombstones_table_name_row_uuid` " +
                "ON `tombstones` (`table_name`, `row_uuid`)"
        )
    }
}

/**
 * 45 -> 46 - a `version` on every row that stands on its own.
 *
 * Twenty-four tables: the documents (ventes, bons d'achat, retours, chargements and their sessions,
 * inventaires, tournées), the single-row records (payments, charges, pertes, price history), and the
 * master data (products, clients, suppliers, the catalogue groups, secteurs, the charge and perte
 * types, commission policies). The eleven tables of lines and links beneath them get none: a change
 * to a line is a change to its document, and bumps the document's `version` instead.
 *
 * `INTEGER NOT NULL DEFAULT 1`, and every existing row starts at 1. Nothing is backfilled: no
 * earlier edit was counted, and 1 means only "the version this row had when counting began".
 *
 * The migration adds the column and nothing else. What moves it is in ChangeTracking.kt — the
 * `updated_at` trigger bumps it on the row's own edits, [DocumentTriggers] on its lines' — installed
 * when the database opens, like the triggers before them.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            // Master data
            "products", "clients", "suppliers", "categories", "sous_categories", "marques", "secteurs",
            "charge_types", "charge_subtypes", "perte_types", "target_policies",
            // Documents and single-row records
            "ventes", "purchase_orders", "retour_client", "retour_fournisseur",
            "chargement_sessions", "chargements", "inventory_sessions", "tournees",
            "client_payments", "supplier_payments", "charges", "pertes", "price_history",
        ).forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        }
    }
}

/**
 * 46 -> 47 - the stock ledger takes over `products.stock` and `products.camion_stock`.
 *
 * From this version the two columns are caches of the ledger — stock movements, plus chargement
 * lines for the camion's share — kept by the triggers in StockLedger.kt, which the app installs when
 * the database opens. Before those triggers recompute anything, this migration makes sure recomputing
 * changes nothing a user can see.
 *
 * Every write used to adjust the columns by hand next to the movement it recorded, so a database can
 * hold stock the ledger does not explain: stock given to a product when it was created, or typed
 * over later. Where a product's columns and its ledger disagree, the difference is recorded as what
 * it is — an `ajustement` named "Reprise du stock", dated now, one at the camion for the camion's
 * share and one at the dépôt for the rest — so the ledger ends up saying exactly what the columns
 * said. A device whose stock always came from movements gets no row at all.
 *
 * Differences under a millionth are rounding in the old incremental arithmetic, not stock, and are
 * left for the recompute below to absorb.
 *
 * The camion adjustment is inserted first; the dépôt one is measured after it, since a camion movement
 * also counts towards the total.
 *
 * Also adds `index_chargement_items_product_id`, which each recompute reads transfer lines through.
 *
 * The ledger sums and the uuid expression are written out here rather than shared with StockLedger.kt
 * and MIGRATION_42_43: this migration records what version 47 did.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chargement_items_product_id` ON `chargement_items` (`product_id`)"
        )

        val ledgerTotal =
            "(SELECT COALESCE(SUM(CASE WHEN m.direction = 'entree' THEN m.quantity ELSE -m.quantity END), 0.0) " +
                "FROM stock_movements m WHERE m.product_id = p.id)"
        val ledgerCamion =
            "((SELECT COALESCE(SUM(CASE WHEN m.direction = 'entree' THEN m.quantity ELSE -m.quantity END), 0.0) " +
                "FROM stock_movements m WHERE m.product_id = p.id AND m.emplacement = 'camion') + " +
                "(SELECT COALESCE(SUM(CASE WHEN c.direction = 'vers_camion' THEN c.quantity ELSE -c.quantity END), 0.0) " +
                "FROM chargement_items c WHERE c.product_id = p.id))"
        val uuidV4 =
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', 1 + (random() & 3), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"
        val now = java.time.Instant.now().toString()
        val nowMs = System.currentTimeMillis()

        fun recordGaps(emplacement: String, gap: String) = db.execSQL(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, " +
                "source_label, source_type, source_id, unit_price, total_value, user_name, note, created_at, " +
                "uuid, updated_at) " +
                "SELECT id, name, 'ajustement', CASE WHEN gap > 0 THEN 'entree' ELSE 'sortie' END, abs(gap), " +
                "'$emplacement', 'Reprise du stock', 'product', id, purchase_price, abs(gap) * purchase_price, " +
                "NULL, 'Stock enregistré sans mouvement avant la mise à niveau', '$now', $uuidV4, $nowMs " +
                "FROM (SELECT p.id, p.name, p.purchase_price, $gap AS gap FROM products p) " +
                "WHERE abs(gap) > 0.000001"
        )
        recordGaps("camion", "p.camion_stock - $ledgerCamion")
        recordGaps("depot", "(p.stock - $ledgerTotal) - (p.camion_stock - $ledgerCamion)")

        db.execSQL("UPDATE products SET stock = ${ledgerTotal.replace("p.id", "products.id")}, " +
            "camion_stock = ${ledgerCamion.replace("p.id", "products.id")}")
    }
}

/**
 * 47 -> 48 - which device a row came from, and which device has the database.
 *
 *  - **`app_meta`**, a key/value table about the database itself. It starts empty: the app writes
 *    `database_id` and `device_id` into it when it opens (see DeviceTracking.kt), because the device's
 *    id lives outside the database, where a migration cannot reach it.
 *  - **`origin_device_id`** on the 24 tables that carry a `version`. Every existing row keeps NULL:
 *    nothing recorded where it was made, and "this phone" would be wrong for a database restored from
 *    another one. New rows are stamped by an insert trigger.
 *  - **`tombstones.device_id`**, likewise NULL for the deletes already recorded.
 *
 * The CREATE statement is copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/48.json`). **Do not hand-edit it** -
 * change the entity, rebuild, and re-copy.
 */
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
        )
        listOf(
            "products", "clients", "suppliers", "categories", "sous_categories", "marques", "secteurs",
            "charge_types", "charge_subtypes", "perte_types", "target_policies",
            "ventes", "purchase_orders", "retour_client", "retour_fournisseur",
            "chargement_sessions", "chargements", "inventory_sessions", "tournees",
            "client_payments", "supplier_payments", "charges", "pertes", "price_history",
        ).forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `origin_device_id` TEXT")
        }
        db.execSQL("ALTER TABLE `tombstones` ADD COLUMN `device_id` TEXT")
    }
}

/**
 * 48 -> 49 - a printed number on ventes, bons d'achat and both kinds of return.
 *
 * Adds a nullable `numero` to the four tables and gives every existing document its id as its number,
 * which is exactly what its receipt, list row and detail screen already show — "Vente #26" stays #26.
 * Stored rather than derived, because after a sync the same document has a different local id on
 * another phone and must still read #26 there.
 *
 * New documents are numbered `V-6DED-000124` by the triggers in DocumentNumbers.kt, whose counters
 * start, the first time the database opens, after the highest id each table ever used.
 */
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("ventes", "purchase_orders", "retour_client", "retour_fournisseur").forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `numero` TEXT")
            db.execSQL("UPDATE `$table` SET `numero` = CAST(`id` AS TEXT)")
        }
    }
}

/**
 * 49 -> 50 - the built-in charge and perte types take the uuids every phone gives them. No schema
 * change.
 *
 * Until now each phone seeded "Carburant", "Casse" and the rest with random uuids, so two phones held
 * two different "Casse" types as far as a sync could tell. They are now seeded with name-based uuids
 * of fixed keys (see defaultTypeUuid), and this migration gives the ones already on a device those
 * same uuids.
 *
 * A built-in is found by `is_default = 1` and the exact name it was seeded with — a built-in type can
 * be neither renamed nor deleted, so the name is still the one it was created with. A subtype is also
 * matched through its built-in parent's name, since "Divers" is both a type and a subtype of Achats.
 * Should a device somehow hold the same built-in twice, only the first gets the uuid (the index on
 * `uuid` is unique); the other keeps its own.
 *
 * Nothing refers to a type by uuid — charges and pertes hold `type_id` — so no other row changes, and
 * changing `uuid` is not an edit to the `updated_at` trigger. A built-in not found by name keeps its
 * random uuid; returns still find it by name (PerteRepository.findDefaultPerteType).
 *
 * The keys and names are written out here rather than read from the seed lists: this migration records
 * what version 50 did. `defaultTypeUuid` itself must never change.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val perteTypes = listOf(
            "casse" to "Casse", "peremption" to "Péremption", "vol" to "Vol",
            "perte_transport" to "Perte de transport", "don" to "Don", "autre" to "Autre",
        )
        for ((key, name) in perteTypes) {
            db.execSQL(
                "UPDATE perte_types SET uuid = ? WHERE id = " +
                    "(SELECT MIN(id) FROM perte_types WHERE is_default = 1 AND name = ?)",
                arrayOf(com.distrigo.app.data.model.defaultTypeUuid("perte_type", key), name),
            )
        }

        // type key, type name, subtypes as key to name
        val chargeTypes = listOf(
            Triple("vehicule", "Véhicule", listOf(
                "carburant" to "Carburant", "pneus" to "Pneus", "reparation" to "Réparation",
                "vidange" to "Vidange", "assurance" to "Assurance")),
            Triple("personnel", "Personnel", listOf(
                "salaire" to "Salaire", "prime" to "Prime", "formation" to "Formation")),
            Triple("bureau", "Bureau", listOf(
                "loyer" to "Loyer", "electricite" to "Électricité", "internet" to "Internet", "fournitures" to "Fournitures")),
            Triple("distribution", "Distribution", listOf(
                "peage" to "Péage", "parking" to "Parking", "livraison" to "Livraison", "emballage" to "Emballage")),
            Triple("achats", "Achats", listOf(
                "materiel" to "Matériel", "nettoyage" to "Nettoyage", "divers" to "Divers")),
            Triple("divers", "Divers", listOf(
                "imprevu" to "Imprévu", "autre" to "Autre")),
        )
        val builtInType = "(SELECT MIN(id) FROM charge_types WHERE is_default = 1 AND name = ?)"
        for ((typeKey, typeName, subtypes) in chargeTypes) {
            db.execSQL(
                "UPDATE charge_types SET uuid = ? WHERE id = $builtInType",
                arrayOf(com.distrigo.app.data.model.defaultChargeTypeUuid(typeKey), typeName),
            )
            for ((subKey, subName) in subtypes) {
                db.execSQL(
                    "UPDATE charge_subtypes SET uuid = ? WHERE id = " +
                        "(SELECT MIN(id) FROM charge_subtypes WHERE is_default = 1 AND name = ? AND type_id = $builtInType)",
                    arrayOf(com.distrigo.app.data.model.defaultChargeSubTypeUuid(typeKey, subKey), subName, typeName),
                )
            }
        }
    }
}

/**
 * 50 -> 51 - two rules the code enforced become rules the database enforces.
 *
 *  - **A product is counted once per inventory session**: `inventory_items(session_id, product_id)`,
 *    until now an ordinary index, becomes unique.
 *  - **A client is planned once per tournée**: a new unique index on `tournee_clients(tournee_id,
 *    client_id)`.
 *
 * Both were checked in Kotlin by reading and then inserting outside a single transaction, so two quick
 * taps could get past the check together. A database holding such a duplicate could not take the index,
 * so any duplicates are merged first — in a way that changes no stock and loses no progress.
 *
 * **Scans.** Two scans of one product in one session count it twice: the first brought the stock from
 * its system quantity to the first count, the second to the second count, each through its own
 * adjustment movement. The merged scan keeps the **latest** row — its count is the last one made — but
 * measured from the **first** row's system quantity, so its écart is the whole correction. The earlier
 * rows' adjustment movements are moved onto it rather than deleted, so the stock is exactly what it was;
 * and editing or deleting the merged scan later removes them all, as it removes its own.
 *
 * **Tournée clients.** The repeated rows describe the same planned visit and were updated together, but
 * the kept one is the furthest along — visited, then in progress, then to visit — first by id on a tie,
 * so no visit is forgotten.
 *
 * The CREATE statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/51.json`). **Do not hand-edit them** -
 * change the entity, rebuild, and re-copy. The inventory index keeps its name, so the old one is dropped
 * first.
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Scans: one row per (session, product) ──
        val sameScan = "i2.session_id = inventory_items.session_id AND i2.product_id = inventory_items.product_id"
        val hasLaterDuplicate =
            "EXISTS (SELECT 1 FROM inventory_items i2 WHERE $sameScan AND i2.id > inventory_items.id)"
        val hasEarlierDuplicate =
            "EXISTS (SELECT 1 FROM inventory_items i2 WHERE $sameScan AND i2.id < inventory_items.id)"
        // The kept (latest) row is measured from the first row's system quantity...
        db.execSQL(
            "UPDATE inventory_items SET qte_systeme = " +
                "(SELECT i2.qte_systeme FROM inventory_items i2 WHERE $sameScan ORDER BY i2.id LIMIT 1) " +
                "WHERE $hasEarlierDuplicate AND NOT $hasLaterDuplicate"
        )
        db.execSQL(
            "UPDATE inventory_items SET ecart = qte_physique - qte_systeme, " +
                "valeur_ecart = (qte_physique - qte_systeme) * purchase_price_snapshot " +
                "WHERE $hasEarlierDuplicate AND NOT $hasLaterDuplicate"
        )
        // ...takes over the earlier rows' adjustment movements...
        db.execSQL(
            "UPDATE stock_movements SET source_id = (" +
                "SELECT MAX(k.id) FROM inventory_items k JOIN inventory_items d " +
                "ON d.session_id = k.session_id AND d.product_id = k.product_id WHERE d.id = stock_movements.source_id" +
                ") WHERE source_type = 'inventory_item' AND source_id IN " +
                "(SELECT id FROM inventory_items WHERE $hasLaterDuplicate)"
        )
        // ...and the earlier rows go.
        db.execSQL("DELETE FROM inventory_items WHERE $hasLaterDuplicate")
        db.execSQL("DROP INDEX IF EXISTS `index_inventory_items_session_id_product_id`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_items_session_id_product_id` " +
                "ON `inventory_items` (`session_id`, `product_id`)"
        )

        // ── Tournée clients: one row per (tournée, client), the furthest along ──
        val progress = "CASE t2.status WHEN 'visite' THEN 2 WHEN 'en_cours' THEN 1 ELSE 0 END"
        db.execSQL(
            "DELETE FROM tournee_clients WHERE id != (" +
                "SELECT t2.id FROM tournee_clients t2 " +
                "WHERE t2.tournee_id = tournee_clients.tournee_id AND t2.client_id = tournee_clients.client_id " +
                "ORDER BY $progress DESC, t2.id ASC LIMIT 1)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tournee_clients_tournee_id_client_id` " +
                "ON `tournee_clients` (`tournee_id`, `client_id`)"
        )
    }
}

/**
 * 51 -> 52 - the business identity receipts print moves into the database: `business_settings`.
 *
 * The table starts empty. The name, phone and logo it replaces are in SharedPreferences and a file,
 * which a migration cannot read; BusinessSettingsRepository copies them into the row the first time the
 * app reads its settings, then removes the old copies.
 *
 * The CREATE statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/52.json`). **Do not hand-edit them** -
 * change the entity, rebuild, and re-copy.
 */
val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `business_settings` (" +
                "`id` INTEGER NOT NULL, " +
                "`business_name` TEXT, " +
                "`business_phone` TEXT, " +
                "`logo_ref` TEXT, " +
                "`uuid` TEXT NOT NULL DEFAULT '', " +
                "`created_at` TEXT NOT NULL DEFAULT '', " +
                "`updated_at` INTEGER NOT NULL DEFAULT 0, " +
                "`version` INTEGER NOT NULL DEFAULT 1, " +
                "`origin_device_id` TEXT, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_business_settings_uuid` ON `business_settings` (`uuid`)"
        )
    }
}

/**
 * 52 -> 53 - a product answers to several barcodes: `product_barcodes`.
 *
 * One row per code, ordered by a dense `position` whose 0 is the primary code (see
 * ProductBarcodeEntity). `products.barcode` stays, as the mirror of that primary code, so everything
 * that shows a single code is untouched.
 *
 * Each product's existing barcode is seeded as its primary code, trimmed, skipping empty ones. Products
 * in the bin are seeded too, so a restore brings their code back. A seeded row takes its product's
 * `created_at`, its own uuid (the version-4 expression of MIGRATION_42_43, written out again because
 * this migration records what version 53 did), and the moment of the upgrade as `updated_at`.
 * `products` itself is not written.
 *
 * The CREATE statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/53.json`). **Do not hand-edit them** -
 * change the entity, rebuild, and re-copy.
 */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `product_barcodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`product_id` INTEGER NOT NULL, " +
                "`code` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`units` INTEGER NOT NULL DEFAULT 1, " +
                "`created_at` TEXT NOT NULL, " +
                "`uuid` TEXT NOT NULL DEFAULT '', " +
                "`updated_at` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_product_barcodes_product_id_position` " +
                "ON `product_barcodes` (`product_id`, `position`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_barcodes_code` ON `product_barcodes` (`code`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_barcodes_uuid` ON `product_barcodes` (`uuid`)")

        val uuidV4 =
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', 1 + (random() & 3), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"
        db.execSQL(
            "INSERT INTO `product_barcodes` (`product_id`, `code`, `position`, `units`, `created_at`, `uuid`, `updated_at`) " +
                "SELECT `id`, TRIM(`barcode`), 0, 1, `created_at`, $uuidV4, ${System.currentTimeMillis()} FROM `products` " +
                "WHERE `barcode` IS NOT NULL AND TRIM(`barcode`) != ''"
        )
    }
}

/**
 * 53 -> 54 - the two indexes a product's price history reads its document lines through.
 *
 * The history is taken from the documents themselves — the bon lines and the vente lines naming the
 * product — so that every entry can name and open the document it came from. Both tables were
 * indexed by their parent document only, the opposite direction, so each read scanned the table.
 *
 * Nothing but the indexes changes: no column, row or table is touched.
 *
 * The statements are copied from Room's generated schema
 * (`app/schemas/com.distrigo.app.data.local.database.AppDatabase/54.json`). **Do not hand-edit them** -
 * change the entity, rebuild, and re-copy.
 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vente_items_product_id` ON `vente_items` (`product_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_order_items_product_id` ON `purchase_order_items` (`product_id`)"
        )
    }
}

/**
 * Every registered migration, in order. The one list both the app's builder and the migration
 * tests read, so a migration that is written but not added here fails the tests instead of
 * shipping unregistered.
 *
 * Declared after the migrations on purpose: top-level properties initialise in file order.
 */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36,
    MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40,
    MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44,
    MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48,
    MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52,
    MIGRATION_52_53, MIGRATION_53_54,
)

/**
 * The versions a database may still be wiped from: every version before [MIGRATION_32_33].
 *
 * Their schemas were never exported, so they cannot be migrated, and those installs have always
 * been recreated on upgrade. From 32 on, every version has a registered path, so a missing one is
 * a bug that should crash on open, not a reason to delete someone's ledger.
 *
 * Nothing here may be the start or end version of a migration in [ALL_MIGRATIONS]: Room rejects
 * that combination when the database is built.
 */
internal val DESTRUCTIVE_MIGRATION_VERSIONS: IntArray = (1 until FIRST_MIGRATABLE_VERSION).toList().toIntArray()

/** The oldest version that migrates to [DATABASE_VERSION] without losing data: the start of [MIGRATION_32_33]. */
const val FIRST_MIGRATABLE_VERSION = 32
