package com.distrigo.app.ui.admin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// What a new batch becomes once shared (docs/activation_codes.md, step C4): a WhatsApp message for quick sales.

private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

internal fun day(date: LocalDate): String = DAY.format(date)

/** A subscription's length, as a merchant says it. */
internal fun duration(days: Int): String = when (days) {
    30, 31 -> "1 mois"
    90 -> "3 mois"
    182, 183 -> "6 mois"
    365, 366 -> "1 an"
    else -> if (days % 30 == 0) "${days / 30} mois" else "$days jours"
}

/** The batch as one WhatsApp message: what the codes give, until when, the codes, and how to use one. */
internal fun whatsAppText(batch: CreatedBatch): String = buildString {
    appendLine("*DistriGo — codes d'activation*")
    appendLine("Lot : ${batch.order.batch}")
    appendLine("DistriGo Solo · ${duration(batch.order.days)} par code")
    batch.codes.firstOrNull()?.expiresOn?.let { appendLine("À activer avant le ${day(it)}") }
    batch.order.businessName?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("Entreprise : $it") }
    appendLine()
    batch.codes.forEachIndexed { i, code -> appendLine("${i + 1}. ${code.code}") }
    appendLine()
    append("Pour activer : ouvrez DistriGo, puis « Activer avec un code ».")
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
