package com.distrigo.app.ui.pertes

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
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.formatQty
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
 * A perte, read-only — the same flow as an expense's details (ChargeDetailScreen). Tapping one in its
 * list used to open the edit form, where a stray touch could change a record that moves stock. Here
 * nothing is editable: "Modifier" asks first, then opens the form; "Supprimer" names the quantity and
 * its value, and says the quantity goes back to stock.
 *
 * A perte made by a client or supplier return is shown too, but has no actions: it changes with that
 * return, and the screen says so instead of the toast its list row used to show.
 */
@Composable
fun PerteDetailScreen(
    onBack    : () -> Unit,
    onDeleted : (typeId: Int) -> Unit,
    viewModel : PerteDetailViewModel = hiltViewModel()
) {
    val money = LocalMoneyFormatter.current
    val vm = viewModel
    var confirmEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Each time the screen shows: back from the form, it shows what was saved.
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.missing) { if (vm.missing) onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Détail de la perte", leading = DsTopBarLeading.Back(onBack))

        val perte = vm.perte
        if (perte == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return@Column
        }

        Column(
            modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            // ── What was lost, how much, and what it was worth ──
            val tint = vm.type?.let { PerteIconMapper.colorFor(it.color_hex) } ?: DsColors.Danger
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
                    EntityImage(ref = perte.product_image_uri, contentDescription = null, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(DsSpacing.md))
                Text(perte.product_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(DsSpacing.xs))
                Text(
                    "${formatQty(perte.quantity)} ${ProductUnit.plural(perte.unit, perte.quantity)}",
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = DsColors.TextPrimary
                )
                Text(
                    money.da(perte.valeur_totale),
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.Danger
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(PerteIconMapper.iconFor(vm.type?.icon ?: "category"), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(perte.type_name, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
                }
            }

            if (vm.isLinked) {
                Row(
                    modifier          = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.WarningLight).padding(DsSpacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = DsColors.Warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(
                        "Cette perte provient d'un ${linkedSource(perte)}. Elle se modifie ou s'annule depuis ce retour.",
                        fontSize = DsTextSize.bodySmall,
                        color    = DsColors.TextPrimary
                    )
                }
            }

            // ── The rest, read-only ──
            val at = runCatching { Instant.parse(perte.date_time).atZone(ZoneId.systemDefault()) }.getOrNull()
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                DetailRow(Icons.Default.Event, "Date", at?.format(DATE)?.replaceFirstChar { it.uppercase() } ?: "—")
                DetailRow(Icons.Default.Warehouse, "Source du stock", if (perte.source == "camion") "Camion" else "Dépôt")
                DetailRow(Icons.Default.Sell, "Prix d'achat unitaire", money.da(perte.purchase_price_snapshot))
                perte.motif?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.Notes, "Motif", it) }
                runCatching { Instant.parse(perte.created_at).atZone(ZoneId.systemDefault()) }.getOrNull()?.let { created ->
                    DetailRow(Icons.Default.History, "Enregistrée le", "${created.format(DAY)} à ${created.format(TIME)}")
                }
                perte.photo_path?.takeIf { it.isNotBlank() }?.let { photo ->
                    EntityImage(
                        ref                = photo,
                        contentDescription = "Photo de la perte",
                        modifier           = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(DsShapes.large)
                    ) {}
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))
        }

        vm.error?.let {
            Text(it, color = DsColors.Danger, fontSize = DsTextSize.bodySmall, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }

        // ── The only way to change it: two actions, each confirmed. None for a linked perte. ──
        if (!vm.isLinked) {
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
        } else {
            Spacer(Modifier.height(DsSpacing.lg))
        }
    }

    val perte = vm.perte
    if (confirmEdit && perte != null) {
        AlertDialog(
            onDismissRequest = { confirmEdit = false },
            title            = { Text("Modifier la perte") },
            text             = { Text("Voulez-vous modifier cette perte ?") },
            confirmButton    = { TextButton(onClick = { confirmEdit = false; editing = true }) { Text("Modifier") } },
            dismissButton    = { TextButton(onClick = { confirmEdit = false }) { Text("Annuler") } },
            containerColor   = DsColors.Surface
        )
    }
    // "Modifier": the type and the quantity, in the dialog a new perte is entered with.
    if (editing && perte != null) {
        PerteDialog(
            productName = perte.product_name, unit = perte.unit,
            stock = vm.stockBefore(), cap = vm.cap(),
            types = vm.types, initialType = perte.type_id, initialQty = perte.quantity, initialMotif = perte.motif,
            isSaving = vm.saving, error = vm.saveError,
            onSave = { typeId, qty, motif -> vm.update(typeId, qty, motif) { editing = false } },
            onDismiss = { editing = false }
        )
    }
    if (confirmDelete && perte != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon             = { Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger) },
            title            = { Text("Supprimer la perte") },
            text             = { Text(deletePerteQuestion(perte, money)) },
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

/**
 * "Êtes-vous sûr de vouloir supprimer cette perte de 2 carton (1 200,00 DA) ? La quantité sera restaurée
 * au stock." — the list's long press asks the same.
 */
internal fun deletePerteQuestion(perte: Perte, money: MoneyFormatter): String =
    "Êtes-vous sûr de vouloir supprimer cette perte de ${formatQty(perte.quantity)} ${ProductUnit.plural(perte.unit, perte.quantity)} " +
        "(${money.da(perte.valeur_totale)}) ? La quantité sera restaurée au stock."

private fun linkedSource(perte: Perte): String =
    if (perte.source_type == "retour_client") "retour client" else "retour fournisseur"

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
