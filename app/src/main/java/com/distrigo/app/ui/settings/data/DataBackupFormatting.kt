package com.distrigo.app.ui.settings.data

import com.distrigo.app.R
import com.distrigo.app.data.backup.RestoreCoordinator
import com.distrigo.app.data.backup.auto.AutoBackupProblem
import com.distrigo.app.data.backup.auto.AutoBackupState
import com.distrigo.app.ui.common.relativeTime
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** How the Sauvegarde et restauration screen writes sizes, dates and table names. */
object DataBackupFormatting {

    private val FRENCH = Locale.FRENCH

    /** `820 Ko`, `1,4 Mo`, `12 Mo`: one decimal below 10 Mo, as a file manager shows it. */
    fun size(bytes: Long): String = when {
        bytes < 1024 -> text(R.string.backup_size_bytes, bytes.toInt())
        bytes < 1024 * 1024 -> text(R.string.backup_size_kb, ((bytes + 1023) / 1024).toInt())
        else -> {
            val mb = bytes / (1024.0 * 1024.0)
            text(R.string.backup_size_mb, if (mb < 10) String.format(FRENCH, "%.1f", mb) else Math.round(mb).toString())
        }
    }

    private val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.formatter("d MMMM yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    private fun text(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
        com.distrigo.app.core.text.AppStrings.write(com.distrigo.app.core.text.UiText.Res(id, args.toList()))

    /** `17 septembre 2026 à 14:30`, in the phone's time. */
    fun dateTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        at.atZone(zone).let { text(R.string.backup_date_time, DAY.format(it), TIME.format(it)) }

    /** `17 septembre 2026 à 03:02 (il y a 5 h)`. */
    fun dateTimeWithAge(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime(at, zone) + (relativeTime(at.toString(), now)?.let { " (${com.distrigo.app.core.text.AppStrings.write(it)})" } ?: "")

    /**
     * The last run that did not save a backup, when it came after the last backup and found nothing wrong: the
     * user sees the schedule is running on nights without changes. Null when the last run saved one, or failed.
     */
    fun lastCheck(state: AutoBackupState, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
        val attempt = state.lastAttemptAt ?: return null
        if (state.lastProblem != null) return null
        val lastBackup = state.lastAt
        if (lastBackup != null && !attempt.isAfter(lastBackup.plusSeconds(60))) return null
        return text(R.string.backup_nothing_changed, dateTimeWithAge(attempt, now, zone))
    }

    /** The problem, and for how long it has lasted when it is not the first time. */
    fun problem(problem: AutoBackupProblem, streak: Int): String =
        if (streak >= 2) text(R.string.backup_failures_in_a_row, problem.message, streak) else problem.message

    /** The tables the preview compares, as the user knows them. */
    fun tableLabel(table: String): String = when (table) {
        "products" -> text(R.string.backup_table_products)
        "clients" -> text(R.string.backup_table_clients)
        "suppliers" -> text(R.string.backup_table_suppliers)
        "ventes" -> text(R.string.backup_table_ventes)
        "purchase_orders" -> text(R.string.backup_table_purchase_orders)
        "stock_movements" -> text(R.string.backup_table_stock_movements)
        else -> table
    }

    /** `153 produits · 18 clients · 27 ventes · 19 bons d'achat`, singular where there is one. */
    fun countsSummary(counts: Map<String, Long>): String =
        listOf("products" to R.plurals.common_products_count, "clients" to R.plurals.common_clients_count,
            "ventes" to R.plurals.ventes_count, "purchase_orders" to R.plurals.lists_n_purchase_bons)
            .joinToString(" · ") { (table, id) ->
                com.distrigo.app.core.text.AppStrings.write(com.distrigo.app.core.text.UiText.Plural(id, (counts[table] ?: 0L).toInt()))
            }

    private val SAFETY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /** When a safety backup was taken, from its name; its file's time if the name does not say. */
    fun safetyBackupTime(file: File, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val stamp = RestoreCoordinator.stamp(file)
        return try {
            LocalDateTime.parse(stamp, SAFETY_STAMP).atZone(zone).toInstant()
        } catch (e: DateTimeParseException) {
            Instant.ofEpochMilli(file.lastModified())
        }
    }
}
