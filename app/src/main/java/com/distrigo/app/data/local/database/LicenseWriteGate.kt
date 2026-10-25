package com.distrigo.app.data.local.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The license's rule in the database (docs/license_l5_enforcement.md §3): when the license does not allow
 * recording, no document, line, payment, stock movement or catalogue row can be written, whatever code path
 * tries.
 *
 * ### How
 *
 * Each table in [GATED_TABLES] carries a `BEFORE INSERT`, a `BEFORE UPDATE` and a `BEFORE DELETE` trigger that
 * raises [REFUSAL] while `app_meta.license.write_gate` reads [CLOSED]. Any other value, or no row at all, lets
 * the write through, so a database the license never judged (the tests', a backup being prepared) behaves as
 * it always did. Like the other triggers ([UpdatedAtTriggers] and the rest), they are built at every open and
 * only rewritten when they differ.
 *
 * The screens (LicensePolicy, L5b) keep the merchant from meeting the gate; the gate is what holds when a
 * screen, a stale draft or a future feature forgets.
 *
 * ### Who writes the value
 *
 * The license, never the data: the app's database writes [licenseAllows] at every open
 * ([withLicenseGate]), and LicenseManager writes it again at every change of state (AppMetaWriteGate). A
 * restored backup's value therefore never counts: a restore restarts the app, and the open rewrites it.
 *
 * ### What stays open
 *
 * The drafts, the settings, `app_meta`, `tombstones` and `price_history` ([NOT_GATED], with why), so drafts can
 * still be typed, settings changed and backups made and restored. A few writes go through a closed gate on
 * purpose, inside [allowing]: see [Allowance].
 *
 * Migrations run with the gate lifted ([lifting]): a database closed when the app was updated must still
 * migrate. The next open builds the triggers again.
 */
object LicenseWriteGate {

    /** The `app_meta` key holding [OPEN] or [CLOSED]. */
    const val KEY = "license.write_gate"
    const val OPEN = "open"
    const val CLOSED = "closed"

    /** The message SQLite's refusal carries; [isRefusal] recognises it. */
    const val REFUSAL = "LICENSE_READ_ONLY"

    /** What a refused write tells the user (§2, « Consultation seule »). */
    const val MESSAGE = "Consultation seule : votre abonnement ne permet plus d'enregistrer. " +
        "Vos données restent consultables ; sauvegarde et export disponibles."

    /** Every document, its lines, money and stock (§3), then the catalogue (decision 1). */
    val GATED_TABLES: List<String> = listOf(
        // Documents and their lines
        "ventes", "vente_items",
        "purchase_orders", "purchase_order_items",
        "retour_client", "retour_client_items",
        "retour_fournisseur", "retour_fournisseur_items",
        "pertes",
        "inventory_sessions", "inventory_items",
        "chargement_sessions", "chargements", "chargement_items",
        "charges",
        "client_payments", "supplier_payments",
        "tournees", "tournee_clients", "tournee_secteurs",
        "stock_movements",
        "commission_periods",
        // The catalogue: products, clients, suppliers and what classifies them
        "products", "product_images", "product_barcodes",
        "categories", "sous_categories", "marques",
        "clients", "suppliers", "secteurs", "quartiers",
        "charge_types", "charge_subtypes", "perte_types",
    )

    /** Every other table, and why it stays open. A table in neither list fails LicenseWriteGateTest. */
    val NOT_GATED: Map<String, String> = mapOf(
        "vente_drafts" to "a draft is not a document (decision 2)",
        "purchase_drafts" to "a draft is not a document (decision 2)",
        "tournee_vente_drafts" to "a draft is not a document (decision 2)",
        "chargement_drafts" to "a draft is not a document (decision 2)",
        "business_settings" to "Paramètres stay open",
        "target_policies" to "the commission policy is edited in Paramètres",
        "policy_tiers" to "the commission policy's tiers, edited with it",
        "app_meta" to "holds the gate itself, the counters and the backup facts",
        "tombstones" to "written by the deletes, which the gate already refuses",
        "price_history" to "written by the purchases and product edits, which the gate already refuses",
    )

    /** The writes let through a closed gate, each for a reason the license must not stand in the way of. */
    enum class Allowance {
        /** D4: an open tournée can always be closed. */
        TOURNEE_CLOSE,

        /** D4: the truck can always be unloaded toward the dépôt (a chargement with only `vers_depot` lines). */
        TRUCK_UNLOAD,

        /** Moving old base64 photos out to files (ImageBackfill): storage, not a change the user made. */
        IMAGE_FILES,

        /** Recording what an ended commission period achieved: figures already recorded, frozen. */
        COMMISSION_FREEZE,
    }

    /**
     * What the license allows, for the app's database to write when it opens. False until LicenseManager first
     * judges the license: `Unchecked` records nothing (§1). Only the app's database reads it ([withLicenseGate]).
     */
    @Volatile
    var licenseAllows: Boolean = false

    private val EVENTS = listOf("insert", "update", "delete")

    fun triggerName(table: String, event: String): String = "trg_${table}_license_$event"

    fun triggerSql(table: String, event: String): String =
        "CREATE TRIGGER `${triggerName(table, event)}` BEFORE ${event.uppercase()} ON `$table` FOR EACH ROW " +
            "WHEN (SELECT `value` FROM `app_meta` WHERE `key` = '$KEY') = '$CLOSED' " +
            "BEGIN SELECT RAISE(ABORT, '$REFUSAL'); END"

    /** Builds the gate's triggers, and drops those of a table no longer in [GATED_TABLES]. */
    fun install(db: SupportSQLiteDatabase) {
        val wanted = GATED_TABLES.flatMap { table -> EVENTS.map { event -> triggerName(table, event) to triggerSql(table, event) } }
        val names = wanted.map { it.first }.toSet()
        for (stale in installedTriggers(db) - names) db.execSQL("DROP TRIGGER IF EXISTS `$stale`")
        for ((name, sql) in wanted) replaceIfChanged(db, name, sql)
    }

    /** Drops every gate trigger. The next open builds them again. */
    fun drop(db: SupportSQLiteDatabase) {
        for (name in installedTriggers(db)) db.execSQL("DROP TRIGGER IF EXISTS `$name`")
    }

    private fun installedTriggers(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name GLOB 'trg_*_license_*'")
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    fun write(db: SupportSQLiteDatabase, open: Boolean) {
        db.execSQL(
            "INSERT OR REPLACE INTO `app_meta` (`key`, `value`) VALUES (?, ?)",
            arrayOf(KEY, if (open) OPEN else CLOSED),
        )
    }

    fun isClosed(db: SupportSQLiteDatabase): Boolean =
        db.query("SELECT `value` FROM `app_meta` WHERE `key` = ?", arrayOf(KEY))
            .use { c -> c.moveToFirst() && c.getString(0) == CLOSED }

    /**
     * Runs [block] in a transaction that a closed gate lets through: the gate opens at its start and closes
     * again before it commits, so nothing else can write in between (the transaction is the only writer).
     * With [why] null, or a gate already open, it is a plain transaction and the gate decides as always.
     */
    suspend fun <T> allowing(db: AppDatabase, why: Allowance?, block: suspend () -> T): T = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        val lifted = why != null && isClosed(sql)
        if (lifted) write(sql, open = true)
        try {
            block()
        } finally {
            // Also on failure: a caller that catches inside a wider transaction must not find it open.
            if (lifted) write(sql, open = false)
        }
    }

    /** Whether [e], or what caused it, is the gate refusing a write. */
    fun isRefusal(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(8).any {
            it is LicenseReadOnlyException || (it is SQLiteConstraintException && it.message?.contains(REFUSAL) == true)
        }

    /** [migration], with the gate's triggers dropped first: no closed gate can stop an update. */
    fun lifting(migration: Migration): Migration = object : Migration(migration.startVersion, migration.endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            drop(db)
            migration.migrate(db)
        }
    }
}

/** A write the license refused, worded for the user. */
class LicenseReadOnlyException(cause: Throwable? = null) : IllegalStateException(LicenseWriteGate.MESSAGE, cause)

/**
 * The app's own database writes what the license allows each time it opens, before anything else can write
 * to it. Tests and the backup being prepared don't use it: their gate stays as it is.
 */
internal fun RoomDatabase.Builder<AppDatabase>.withLicenseGate(
    allows: () -> Boolean = { LicenseWriteGate.licenseAllows },
): RoomDatabase.Builder<AppDatabase> =
    addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            LicenseWriteGate.write(db, allows())
        }
    })
