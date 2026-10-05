package com.distrigo.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.InventorySessionSummary
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.foundation.clickable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun inventoryNumero(id: Int): String = "N° " + id.toString().padStart(5, '0')

// The count itself is InventoryCountScreen (the list and its dialog) and InventoryCartScreen (its
// selection); this file holds the step that closes it.

/** What to do with the products in stock the count did not reach. */
enum class UncountedChoice { ZERO, KEEP }

/**
 * Résumé de l'inventaire: the count's figures, and — while products in stock were not counted — the
 * decision about them, which must be made before the count can be confirmed: put their dépôt stock to
 * zero, or keep it as it is.
 */
@Composable
fun ColumnScope.InventorySummaryStep(
    date            : LocalDate,
    onDateChange    : (LocalDate) -> Unit,
    summary         : InventorySessionSummary,
    uncounted       : Int?,
    choice          : UncountedChoice?,
    onChoice        : (UncountedChoice) -> Unit,
    isConfirmed     : Boolean,
    isConfirming    : Boolean,
    zeroed          : Int,
    confirmError    : String,
    onBack          : () -> Unit,
    onConfirm       : () -> Unit,
    onReturnHistory : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    // Nothing left uncounted, nothing to decide.
    val needsChoice = !isConfirmed && (uncounted ?: 0) > 0

    DsTopAppBar(
        title   = "Résumé de l'inventaire",
        // Back greys out and stops responding once the session is confirmed — a state
        // DsTopBarLeading.Back has no way to express, so the button rides in Custom.
        leading = DsTopBarLeading.Custom {
            IconButton(onClick = onBack, enabled = !isConfirmed) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = if (isConfirmed) DsColors.TextTertiary else DsColors.TextPrimary
                )
            }
        }
    )

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        InventoryDateField(date = date, enabled = !isConfirmed, onDateChange = onDateChange)

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            InventorySummaryStatCard(Icons.Default.Inventory2, "${summary.total_products}", "Total produits scannés", DsColors.Primary, Modifier.weight(1f))
            InventorySummaryStatCard(Icons.Default.Warning, "${summary.total_ecarts}", "Écarts détectés", Color(0xFFF79009), Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            InventorySummaryStatCard(Icons.Default.Receipt, money.da(summary.total_value_ecarts), "Valeur des écarts", DsColors.Danger, Modifier.weight(1f))
            InventorySummaryStatCard(Icons.Default.HourglassEmpty, uncounted?.toString() ?: "…", "Produits non inventoriés", DsColors.TextSecondary, Modifier.weight(1f))
        }

        if (needsChoice) {
            Column(
                Modifier.fillMaxWidth().clip(DsShapes.medium).border(1.dp, DsColors.Border, DsShapes.medium).padding(DsSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            ) {
                Text("Produits non inventoriés", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(
                    "${uncounted} produit(s) en stock n'ont pas été comptés. Que faire de leur stock ?",
                    fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
                )
                Spacer(Modifier.height(DsSpacing.xs))
                ChoiceRow(
                    selected = choice == UncountedChoice.ZERO,
                    title    = "Mettre à zéro",
                    detail   = "Leur stock au dépôt passe à 0. Le stock des camions n'est pas touché.",
                    onClick  = { onChoice(UncountedChoice.ZERO) }
                )
                ChoiceRow(
                    selected = choice == UncountedChoice.KEEP,
                    title    = "Conserver le stock actuel",
                    detail   = "Leur stock ne change pas.",
                    onClick  = { onChoice(UncountedChoice.KEEP) }
                )
            }
        }

        if (isConfirmed) {
            Surface(shape = DsShapes.medium, color = Color(0xFF12B76A).copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Column {
                        Text("Inventaire complété avec succès", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF12B76A))
                        Text(
                            if (zeroed > 0) "Le stock a été mis à jour · $zeroed produit(s) mis à zéro" else "Le stock a été mis à jour",
                            fontSize = DsTextSize.caption, color = Color(0xFF12B76A).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (confirmError.isNotEmpty()) {
            Text(confirmError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
        }
    }

    if (isConfirmed) {
        Button(
            onClick  = onReturnHistory,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Text("Retour à l'historique", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick  = onConfirm,
            // The decision about the uncounted products is required before anything is written.
            enabled  = !isConfirming && (!needsChoice || choice != null),
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isConfirming) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.xs))
                Text("Confirmer l'inventaire", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One answer of the uncounted products' question: a radio button, its title and what it does. */
@Composable
private fun ChoiceRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(if (selected) DsColors.PrimaryLight else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = DsSpacing.sm, horizontal = DsSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = DsColors.Primary))
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = if (selected) DsColors.Primary else DsColors.TextPrimary)
            Text(detail, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

/**
 * The inventory's date: "Aujourd'hui" until another day is picked, in the Material date picker, which
 * offers no day after today. Fixed once the inventory is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryDateField(date: LocalDate, enabled: Boolean, onDateChange: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Changer la date") { picking = true }
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text("Date", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            if (date == today) "Aujourd'hui" else date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f)
        )
        if (enabled) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
    }

    if (picking) {
        // The Material picker works in UTC milliseconds; the day tapped is read back in UTC (timestamps.md rule 5).
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun InventorySummaryStatCard(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = DsShapes.medium, color = color.copy(alpha = 0.08f), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(DsSpacing.md)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(DsSpacing.xs))
            FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}
