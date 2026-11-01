package com.distrigo.app.data.backup

import com.distrigo.app.R
import android.database.sqlite.SQLiteDatabase

/**
 * What a restore does to "Autoriser le stock négatif", read from the backup once it is unpacked and
 * before anything on the phone changes.
 *
 * The setting lives in the business's settings row, inside the database, so a restore brings the
 * backup's rule back with its data — on purpose: it is the business's rule for that data, as the money
 * format is. Nothing overrides it. What was missing is the user being told: the switch warns when it is
 * turned off over products already below zero, and a restore can make the same change silently.
 *
 * Products already below zero under a strict rule are not deactivated: DepotStockGuard refuses any write
 * that leaves one lower than it was, so they cannot be sold, loaded or written off from the dépôt, while
 * restocking them stays possible — exactly as after turning the switch off.
 */
data class RestoreStockRule(
    /** The backup's rule, once migrated: true when negative stock is allowed. */
    val backupAllowsNegative: Boolean,
    /** The rule on the phone now. */
    val currentAllowsNegative: Boolean,
    /** Live products below zero in the backup's dépôt. */
    val negativeProducts: Int,
    /**
     * The backup predates the setting: upgrading it added the setting with its default, "allowed",
     * rather than a choice the business made.
     */
    val resetByUpgrade: Boolean,
) {
    /** Whether the restore changes anything about the rule worth a word before it goes ahead. */
    val worthTelling: Boolean
        get() = backupAllowsNegative != currentAllowsNegative || (!backupAllowsNegative && negativeProducts > 0)

    /** The dialog's paragraphs, in order. */
    fun lines(): List<String> = buildList {
        add(backupText(R.string.backup_rule_line, rule(backupAllowsNegative), rule(currentAllowsNegative), rule(backupAllowsNegative)))
        if (resetByUpgrade) {
            add(backupText(R.string.backup_rule_reset))
        }
        if (!backupAllowsNegative && negativeProducts > 0) {
            // The switch's own words, so the two say the same thing about the same products.
            add(backupCount(R.plurals.backup_rule_negative_products, negativeProducts))
        }
        add(backupText(R.string.backup_rule_change_later))
    }

    private fun rule(allows: Boolean) = backupText(if (allows) R.string.backup_rule_allowed else R.string.backup_rule_forbidden)

    companion object {
        /** The database version that added the setting (MIGRATION_56_57). */
        const val SETTING_SINCE_VERSION = 57

        /**
         * Reads the rule from [prepared]'s database, already migrated to this app's version. Opened read-only;
         * the count is ProductDao.countNegativeDepot's.
         */
        fun read(prepared: PreparedRestore, currentAllowsNegative: Boolean): RestoreStockRule {
            val database = SQLiteDatabase.openDatabase(prepared.database.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val allows = database.rawQuery("SELECT allow_negative_stock FROM business_settings WHERE id = 1", null).use {
                    // No settings row yet: the default, allowed.
                    if (it.moveToFirst()) it.getInt(0) != 0 else true
                }
                val negative = database.rawQuery(
                    "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL AND stock - camion_stock < -0.000001", null
                ).use { it.moveToFirst(); it.getInt(0) }
                return RestoreStockRule(
                    backupAllowsNegative  = allows,
                    currentAllowsNegative = currentAllowsNegative,
                    negativeProducts      = negative,
                    resetByUpgrade        = prepared.manifest.schemaVersion < SETTING_SINCE_VERSION,
                )
            } finally {
                database.close()
            }
        }
    }
}
