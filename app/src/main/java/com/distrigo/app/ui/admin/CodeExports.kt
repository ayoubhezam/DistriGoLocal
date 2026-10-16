package com.distrigo.app.ui.admin

import com.distrigo.app.core.format.subscriptionLength
import com.distrigo.app.data.license.CodeOrder
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// What a new batch becomes once shared (docs/activation_codes.md, step C4): a WhatsApp message for quick sales.

private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

internal fun day(date: LocalDate): String = DAY.format(date)

/** « 1 code », « 3 codes » (and « 0 code », as French says it). */
internal fun codes(count: Int): String = if (count > 1) "$count codes" else "$count code"

/** Why « Supprimer le lot » is refused: some of its codes still open a business, which would lose its key. */
internal fun inUse(keys: Int): String =
    "Ce lot ne peut pas être supprimé : " +
        (if (keys > 1) "$keys de ses codes ouvrent une entreprise." else "un de ses codes ouvre une entreprise.")

/** « Autre » on « Nouveau lot »: the unit a typed duration is in, and its days (a month as the « 1 mois » preset). */
internal enum class LengthUnit(val label: String, val days: Int) {
    DAYS("jours", 1),
    MONTHS("mois", 30),
    YEARS("ans", 365),
}

/** The days a typed duration gives: a whole number, from 1 day to CodeOrder.MAX_DAYS; null otherwise. */
internal fun customDays(amount: String, unit: LengthUnit): Int? {
    val count = amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
    return (count.toLong() * unit.days).takeIf { it <= CodeOrder.MAX_DAYS }?.toInt()
}


/** The batch as one WhatsApp message: what the codes give, until when, the codes, and how to use one. */
internal fun whatsAppText(batch: CreatedBatch): String = buildString {
    appendLine("*DistriGo — codes d'activation*")
    appendLine("Lot : ${batch.order.batch}")
    appendLine("DistriGo Solo · ${subscriptionLength(batch.order.days)} par code")
    batch.codes.firstOrNull()?.expiresOn?.let { appendLine("À activer avant le ${day(it)}") }
    batch.order.businessName?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("Entreprise : $it") }
    appendLine()
    batch.codes.forEachIndexed { i, code -> appendLine("${i + 1}. ${code.code}") }
    appendLine()
    // Copied whole, the message carries its code into DistriGo: its code field finds it
    append("Pour activer : copiez ce message, ouvrez DistriGo, touchez « J'ai un code d'activation », puis « Coller ».")
}

/** WhatsApp, or WhatsApp Business; without either, Android's share sheet. */
internal fun shareOnWhatsApp(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    for (app in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            context.startActivity(Intent(send).setPackage(app))
            return
        } catch (e: ActivityNotFoundException) {
            // Not installed: the next one
        }
    }
    context.startActivity(Intent.createChooser(send, "Partager les codes"))
}
