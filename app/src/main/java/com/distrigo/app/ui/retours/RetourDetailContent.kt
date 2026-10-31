package com.distrigo.app.ui.retours

import com.distrigo.app.ui.designsystem.LocalLicense

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
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.common.BuiltInNames
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.quantityText
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

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
    // A blocked write explains itself instead (docs/license_l5_enforcement.md §2)
    val license = LocalLicense.current

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.retours_detail_title), subtitle = stringResource(R.string.retours_number, numberLabel), leading = DsTopBarLeading.Back(onBack))

        Column(
            modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            // ── Who, and how much ──
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .padding(vertical = DsSpacing.xl, horizontal = DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier.size(64.dp).clip(DsShapes.large).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(DsSpacing.md))
                Text(partyName, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.xs))
                Text(money.da(total), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
                lines?.let {
                    Text(productCount(it.size), fontSize = DsTextSize.body, color = DsColors.TextSecondary)
                }
            }

            // ── The rest, read-only ──
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                DetailRow(Icons.Default.Event, stringResource(R.string.inventory_date), dayLabel(date, createdAt))
                DetailRow(Icons.Default.Person, partyLabel, partyName)
                // A return's lines can each have their own motif: then it is "Selon le produit", and each line says its own.
                val lineMotifs = lines?.mapNotNull { it.motif }?.distinct().orEmpty()
                DetailRow(Icons.AutoMirrored.Filled.Label, stringResource(R.string.pertes_motif), when {
                    lineMotifs.size > 1 -> stringResource(R.string.retours_per_product)
                    else -> (motif ?: lineMotifs.firstOrNull())?.takeIf { it.isNotBlank() }?.let(BuiltInNames::motif) ?: "—"
                })
                DetailRow(Icons.Default.Inventory2, stringResource(R.string.retours_stock_effect), stockEffect)
                note?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.AutoMirrored.Filled.Notes, stringResource(R.string.clients_note), it) }
                instant(createdAt)?.let { DetailRow(Icons.Default.History, stringResource(R.string.retours_recorded_on), stringResource(R.string.pertes_day_at_time, it.format(DAY), it.format(TIME))) }
            }

            // ── What came back ──
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(
                    if (lines != null) stringResource(R.string.retours_products_n, lines.size) else stringResource(R.string.common_step_products),
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
            onClick  = license.guarded { confirmDelete = true },
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
                Text(stringResource(R.string.retours_delete), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (confirmDelete || deleteError != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false; onDismissError() },
            icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
            title            = { Text(stringResource(R.string.retours_delete)) },
            text             = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Text(stringResource(R.string.retours_delete_question, money.da(total)), color = DsColors.TextPrimary)
                    Text(stringResource(R.string.ventes_delete_explain), color = DsColors.TextSecondary)
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
                    Text(stringResource(R.string.common_action_delete), color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton    = { TextButton(onClick = { confirmDelete = false; onDismissError() }) { Text(stringResource(R.string.common_action_cancel)) } },
            containerColor   = DsColors.Surface
        )
    }
}

@Composable
private fun LineRow(line: RetourLine, showMotif: Boolean) {
    val money = LocalMoneyFormatter.current
    Row(
        modifier          = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.medium).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            Text(
                stringResource(R.string.common_qty_times_price, quantityText(line.quantity, line.unit).asString(), money.da(line.unitPrice)),
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
            if (showMotif) line.motif?.let(BuiltInNames::motif)?.let {
                Text(it, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.Primary)
            }
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(money.da(line.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
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
@Composable
internal fun productCount(count: Int): String = pluralStringResource(R.plurals.common_products_count, count, count)

private fun instant(text: String) = runCatching { Instant.parse(text).atZone(ZoneId.systemDefault()) }.getOrNull()

/** The return's own day ("2026-09-16"), or the day it was recorded when that is all there is. */
private fun dayLabel(date: String, createdAt: String): String {
    val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: instant(createdAt)?.toLocalDate()
    return day?.format(DATE)?.replaceFirstChar { it.uppercase() } ?: "—"
}

private val DATE get() = com.distrigo.app.core.format.AppDates.formatter("EEEE d MMMM yyyy")
private val DAY get() = com.distrigo.app.core.format.AppDates.formatter("d MMM yyyy")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
