package com.distrigo.app.ui.settings.data

import com.distrigo.app.data.backup.RestoreCoordinator
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** How the Données et sauvegarde screen writes sizes, dates and table names. */
object DataBackupFormatting {

    private val FRENCH = Locale.FRENCH

    /** `820 Ko`, `1,4 Mo`, `12 Mo`: one decimal below 10 Mo, as a file manager shows it. */
    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes octets"
        bytes < 1024 * 1024 -> "${(bytes + 1023) / 1024} Ko"
        else -> {
            val mb = bytes / (1024.0 * 1024.0)
            if (mb < 10) String.format(FRENCH, "%.1f Mo", mb) else "${Math.round(mb)} Mo"
        }
    }

    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", FRENCH)

    /** `17 septembre 2026 à 14:30`, in the phone's time. */
    fun dateTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = DATE_TIME.format(at.atZone(zone))

    /** The tables the preview compares, as the user knows them. */
    fun tableLabel(table: String): String = when (table) {
        "products" -> "Produits"
        "clients" -> "Clients"
        "suppliers" -> "Fournisseurs"
        "ventes" -> "Ventes"
        "purchase_orders" -> "Bons d'achat"
        "stock_movements" -> "Mouvements de stock"
        else -> table
    }

    /** `153 produits · 18 clients · 27 ventes · 19 bons d'achat`, singular where there is one. */
    fun countsSummary(counts: Map<String, Long>): String =
        listOf("products" to ("produit" to "produits"), "clients" to ("client" to "clients"),
            "ventes" to ("vente" to "ventes"), "purchase_orders" to ("bon d'achat" to "bons d'achat"))
            .joinToString(" · ") { (table, words) ->
                val n = counts[table] ?: 0L
                "$n ${if (n == 1L) words.first else words.second}"
            }

    private val SAFETY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /** When a safety backup was taken, from its name; its file's time if the name does not say. */
    fun safetyBackupTime(file: File, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val stamp = file.name.removePrefix(RestoreCoordinator.SAFETY_PREFIX).substringBefore('.')
        return try {
            LocalDateTime.parse(stamp, SAFETY_STAMP).atZone(zone).toInstant()
        } catch (e: DateTimeParseException) {
            Instant.ofEpochMilli(file.lastModified())
        }
    }
}
