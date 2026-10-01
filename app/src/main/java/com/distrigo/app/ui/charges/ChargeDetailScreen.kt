package com.distrigo.app.ui.charges

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Charge
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.core.format.MoneyFormatter

/**
 * An expense, read-only. Tapping one in its list used to open the edit form straight away, where a
 * stray touch could change a financial record; here nothing is editable. "Modifier" asks first, then
 * opens the form; "Supprimer" names the amount before deleting.
 */
@Composable
fun ChargeDetailScreen(
    onBack    : () -> Unit,
    onEdit    : (Charge) -> Unit,
    onDeleted : (SavedCharge) -> Unit,
    viewModel : ChargeDetailViewModel = hiltViewModel()
) {
    val money = LocalMoneyFormatter.current
    val vm = viewModel
    var confirmEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Each time the screen shows: back from the form, it shows what was saved.
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.missing) { if (vm.missing) onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Détail de la dépense", leading = DsTopBarLeading.Back(onBack))

        val charge = vm.charge
        if (charge == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return@Column
        }

        Column(
            modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            // ── The amount, and what it was for ──
            val tint = vm.type?.let { ChargeIconMapper.colorFor(it.color_hex) } ?: DsColors.Primary
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceSunken)
                    .padding(vertical = DsSpacing.xl, horizontal = DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier.size(52.dp).clip(DsShapes.pill).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChargeIconMapper.iconFor(vm.subType?.icon ?: "category"), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(DsSpacing.md))
                Text(
                    money.da(charge.montant),
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = DsColors.TextPrimary
                )
                Spacer(Modifier.height(DsSpacing.xs))
                Text(
                    "${charge.type_name} · ${charge.subtype_name}",
                    fontSize = DsTextSize.body,
                    color    = DsColors.TextSecondary
                )
            }

            // ── The rest, read-only ──
            val at = runCatching { Instant.parse(charge.date_time).atZone(ZoneId.systemDefault()) }.getOrNull()
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .padding(vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                DetailRow(Icons.Default.Event, "Date", at?.format(DATE)?.replaceFirstChar { it.uppercase() } ?: "—")
                DetailRow(Icons.Default.Schedule, "Heure", at?.format(TIME) ?: "—")
                charge.fournisseur?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.Storefront, "Fournisseur / Station", it) }
                charge.note?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.Notes, "Note", it) }
                runCatching { Instant.parse(charge.created_at).atZone(ZoneId.systemDefault()) }.getOrNull()?.let { created ->
                    DetailRow(Icons.Default.History, "Enregistrée le", "${created.format(DAY)} à ${created.format(TIME)}")
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))
        }

        vm.error?.let {
            Text(it, color = DsColors.Danger, fontSize = DsTextSize.bodySmall, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }

        // ── The only way to change it: two actions, each confirmed ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            OutlinedButton(
                onClick  = { confirmDelete = true },
                enabled  = !vm.deleting,
                modifier = Modifier.weight(1f).height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Danger),
                border   = BorderStroke(1.dp, DsColors.Danger)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Supprimer", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick  = { confirmEdit = true },
                enabled  = !vm.deleting,
                modifier = Modifier.weight(1f).height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Modifier", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    val charge = vm.charge
    if (confirmEdit && charge != null) {
        AlertDialog(
            onDismissRequest = { confirmEdit = false },
            title            = { Text("Modifier la dépense") },
            text             = { Text("Voulez-vous modifier cette dépense ?") },
            confirmButton    = { TextButton(onClick = { confirmEdit = false; onEdit(charge) }) { Text("Modifier") } },
            dismissButton    = { TextButton(onClick = { confirmEdit = false }) { Text("Annuler") } },
            containerColor   = DsColors.Surface
        )
    }
    if (confirmDelete && charge != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
            title            = { Text("Supprimer la dépense") },
            text             = { Text(deleteQuestion(charge, money)) },
            confirmButton    = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton    = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
            containerColor   = DsColors.Surface
        )
    }
}

/** "Êtes-vous sûr de vouloir supprimer cette dépense de 3 500,50 DA ?" — the list's long press asks the same. */
internal fun deleteQuestion(charge: Charge, money: MoneyFormatter): String =
    "Êtes-vous sûr de vouloir supprimer cette dépense de ${money.da(charge.montant)} ?"

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

private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
