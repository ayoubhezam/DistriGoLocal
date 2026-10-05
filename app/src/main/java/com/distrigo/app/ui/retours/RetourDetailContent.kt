package com.distrigo.app.ui.retours

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.distrigo.app.ui.format.LocalMoneyFormatter

/** One returned product, as the details list it. */
internal data class RetourLine(
    val id        : Int,
    val name      : String,
    val unit      : String,
    val quantity  : Double,
    val unitPrice : Double,
    val total     : Double,
    /** The line's motif, shown under it when the return's lines do not all share one. */
    val motif     : String? = null
)

/**
 * A return, read-only — client or supplier, the same screen as a perte's or an expense's details.
 *
 * A return is a finished stock document: it is not edited. To correct one, it is deleted — which
 * reverses its stock movements, its linked pertes and the balance — and entered again. So the only
 * action is "Supprimer", and its confirmation says exactly what the delete will do: the amount, the
 * effect on stock, and the effect on the balance ([deleteEffects]).
 */
@Composable
internal fun RetourDetailContent(
    numberLabel   : String,
    partyLabel    : String,
    partyName     : String,
    total         : Double,
    date          : String,
    createdAt     : String,
    motif         : String?,
    stockEffect   : String,
    note          : String?,
    lines         : List<RetourLine>?,
    deleteEffects : List<String>,
    deleting      : Boolean,
    deleteError   : String?,
    onDelete      : () -> Unit,
    onDismissError: () -> Unit,
    onBack        : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Détail du retour", subtitle = "Retour $numberLabel", leading = DsTopBarLeading.Back(onBack))

        Column(
            modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            // ── Who, and how much ──
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceSunken)
                    .padding(vertical = DsSpacing.xl, horizontal = DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier.size(64.dp).clip(DsShapes.large).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(DsSpacing.md))
                Text(partyName, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.xs))
                Text(money.da(total), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
                lines?.let {
                    Text(productCount(it.size), fontSize = DsTextSize.body, color = DsColors.TextSecondary)
                }
            }

            // ── The rest, read-only ──
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                DetailRow(Icons.Default.Event, "Date", dayLabel(date, createdAt))
                DetailRow(Icons.Default.Person, partyLabel, partyName)
                // A return's lines can each have their own motif: then it is "Selon le produit", and each line says its own.
                val lineMotifs = lines?.mapNotNull { it.motif }?.distinct().orEmpty()
                DetailRow(Icons.Default.Label, "Motif", when {
                    lineMotifs.size > 1 -> "Selon le produit"
                    else -> (motif ?: lineMotifs.firstOrNull())?.takeIf { it.isNotBlank() } ?: "—"
                })
                DetailRow(Icons.Default.Inventory2, "Effet sur le stock", stockEffect)
                note?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.Notes, "Note", it) }
                instant(createdAt)?.let { DetailRow(Icons.Default.History, "Enregistré le", "${it.format(DAY)} à ${it.format(TIME)}") }
            }

            // ── What came back ──
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(
                    if (lines != null) "Produits (${lines.size})" else "Produits",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextSecondary
                )
                if (lines == null) {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.lg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                } else {
                    val mixed = lines.mapNotNull { it.motif }.distinct().size > 1
                    lines.forEach { line -> LineRow(line, showMotif = mixed) }
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))
        }

        // ── The only action: a return is deleted, never edited ──
        OutlinedButton(
            onClick  = { confirmDelete = true },
            enabled  = !deleting,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Danger),
            border   = BorderStroke(1.dp, DsColors.Danger)
        ) {
            if (deleting) {
                CircularProgressIndicator(color = DsColors.Danger, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Supprimer le retour", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (confirmDelete || deleteError != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false; onDismissError() },
            icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
            title            = { Text("Supprimer le retour") },
            text             = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Text("Êtes-vous sûr de vouloir supprimer ce retour de ${money.da(total)} ?", color = DsColors.TextPrimary)
                    Text("La suppression annulera ses mouvements de stock et mettra à jour le solde :", color = DsColors.TextSecondary)
                    deleteEffects.forEach { effect ->
                        Row {
                            Text("•  ", color = DsColors.TextSecondary)
                            Text(effect, color = DsColors.TextSecondary)
                        }
                    }
                    // A refused delete is said here — strict stock, for one — instead of the dialog closing silently.
                    deleteError?.let { Text(it, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
                }
            },
            confirmButton    = {
                TextButton(onClick = { confirmDelete = false; onDismissError(); onDelete() }, enabled = !deleting) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton    = { TextButton(onClick = { confirmDelete = false; onDismissError() }) { Text("Annuler") } },
            containerColor   = DsColors.Surface
        )
    }
}

@Composable
private fun LineRow(line: RetourLine, showMotif: Boolean) {
    val money = LocalMoneyFormatter.current
    Row(
        modifier          = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            Text(
                "${formatQty(line.quantity)} ${ProductUnit.plural(line.unit, line.quantity)} × ${money.da(line.unitPrice)}",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
            if (showMotif) line.motif?.let {
                Text(it, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.Primary)
            }
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(money.da(line.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
        }
    }
}

/** "1 produit", "3 produits" — the return lists and details. */
internal fun productCount(count: Int): String = if (count <= 1) "$count produit" else "$count produits"

private fun instant(text: String) = runCatching { Instant.parse(text).atZone(ZoneId.systemDefault()) }.getOrNull()

/** The return's own day ("2026-09-16"), or the day it was recorded when that is all there is. */
private fun dayLabel(date: String, createdAt: String): String {
    val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: instant(createdAt)?.toLocalDate()
    return day?.format(DATE)?.replaceFirstChar { it.uppercase() } ?: "—"
}

private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
