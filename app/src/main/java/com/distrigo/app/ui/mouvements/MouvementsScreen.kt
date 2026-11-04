package com.distrigo.app.ui.mouvements

import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Description
import com.distrigo.app.ui.navigation.LocalDrillDown
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.common.formatQty
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey

import com.distrigo.app.data.time.BusinessDates
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockMovement
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import java.util.Locale
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.common.BuiltInNames
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.unitWordText
import com.distrigo.app.R
import com.distrigo.app.core.text.UiText


@Composable
fun MouvementsScreen(
    product         : Product,
    onBack          : () -> Unit,
    onMovementClick : (StockMovement) -> Unit,
    viewModel       : StockMovementViewModel = hiltViewModel()
) {

    val filters by viewModel.filters.collectAsState()
    // Paged, with the figures summed in SQL for the same filters - see StockMovementViewModel.
    val pagedMovements = viewModel.pagedMovements.collectAsLazyPagingItems()
    val totals    by viewModel.totals.collectAsState()
    val clients   by viewModel.clients.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }
    // Hoisted so a new filter can send the list back to the top: its pages start there again, and a
    // list left at its old index would open mid-way through the new results.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(product.id) { viewModel.loadPartiesForProduct(product.id) }
    LaunchedEffect(product.id) { viewModel.showProduct(product.id) }

    if (filtersOpen) {
        MovementFiltersSheet(
            filters   = filters,
            clients   = clients,
            suppliers = suppliers,
            countOf   = { draft -> viewModel.countFor(product.id, draft) },
            onApply   = {
                if (it != filters) scope.launch { listState.scrollToItem(0) }
                viewModel.setFilters(it)
                filtersOpen = false
            },
            onDismiss = { filtersOpen = false }
        )
    }

    // ── Detail Sub-screen ──

    // ── Main List ──
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title   = stringResource(R.string.mouvements_title),
            leading = DsTopBarLeading.Back(onBack)
        ) {
            Box {
                IconButton(onClick = { filtersOpen = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.common_label_filters),
                        tint = if (filters.activeCount > 0) DsColors.PrimaryDark else DsColors.TextSecondary
                    )
                }
                if (filters.activeCount > 0) {
                    Text(
                        filters.activeCount.toString(),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DsColors.Surface,
                        modifier = Modifier.align(Alignment.TopEnd).clip(DsShapes.pill)
                            .background(DsColors.Danger).padding(horizontal = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Product row ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Column {
                Text(product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                Text(stringResource(R.string.mouvements_stock_now, formatQty(product.stock)), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Stats ──
        val entrees = totals?.entrees ?: 0.0
        val sorties = totals?.sorties ?: 0.0
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.mouvements_in),    value = formatQty(entrees),  color = DsColors.Success)
            StatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.mouvements_out),    value = formatQty(sorties),  color = DsColors.Danger)
            StatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.mouvements_title), value = "${totals?.count ?: 0}", color = DsColors.Primary)
        }

        Spacer(Modifier.height(DsSpacing.md))

        val refresh = pagedMovements.loadState.refresh
        when {
            pagedMovements.itemCount == 0 && refresh is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            pagedMovements.itemCount == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.distrigo.app.ui.common.EmptyState(R.drawable.empty_mouvements_stock, stringResource(R.string.mouvements_none))
                }
            }
            else -> {
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(
                        count       = pagedMovements.itemCount,
                        key         = pagedMovements.itemKey { row ->
                            when (row) {
                                is MovementListItem.DayHeader -> "header_${row.day}"
                                is MovementListItem.Row       -> row.movement.id
                            }
                        },
                        contentType = pagedMovements.itemContentType { row -> row is MovementListItem.DayHeader }
                    ) { index ->
                        when (val row = pagedMovements[index]) {
                            is MovementListItem.DayHeader -> Text(
                                formatMovementDateLabel(row.day),
                                fontSize   = DsTextSize.caption,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(top = DsSpacing.sm, bottom = DsSpacing.xs)
                            )
                            is MovementListItem.Row -> MovementRow(movement = row.movement, onClick = { onMovementClick(row.movement) })
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

// ── Stat card ──
@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

// ── Movement row ──
@Composable
private fun MovementRow(movement: StockMovement, onClick: () -> Unit) {
    val isEntree = movement.direction == "entree"
    val icon = movementTypeIcon(movement.type)
    val typeLabel = movementTypeLabel(movement.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable { onClick() }
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(DsShapes.medium)
                .background(if (isEntree) DsColors.SuccessLight else DsColors.DangerLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isEntree) DsColors.Success else DsColors.Danger, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(movementSourceLabel(movement.type, movement.source_label), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
        }
        Text(
            "${if (isEntree) "+" else "-"}${formatQty(movement.quantity)}",
            fontSize   = DsTextSize.bodyLarge,
            fontWeight = FontWeight.Bold,
            color      = if (isEntree) DsColors.Success else DsColors.Danger
        )
    }
}

// ── Helpers ──
fun movementTypeIcon(type: String): ImageVector = when (type) {
    "achat"      -> Icons.Default.Inventory
    "vente"      -> Icons.Default.PointOfSale
    "chargement" -> Icons.Default.LocalShipping
    "perte"      -> Icons.Default.Warning
    "ajustement" -> Icons.Default.Tune
    "retour_client", "retour_fournisseur" -> Icons.AutoMirrored.Filled.AssignmentReturn
    else         -> Icons.Default.SwapVert
}

/** A movement type's name in the app's language. */
@Composable
fun movementTypeLabel(type: String): String = when (type) {
    "achat"      -> stringResource(R.string.common_kind_purchase)
    "vente"      -> stringResource(R.string.common_kind_sale)
    "chargement" -> stringResource(R.string.common_kind_loading)
    "perte"      -> stringResource(R.string.common_kind_loss)
    "ajustement" -> stringResource(R.string.common_kind_adjustment)
    "retour_client"      -> stringResource(R.string.common_kind_client_return)
    "retour_fournisseur" -> stringResource(R.string.common_kind_supplier_return)
    else         -> type
}

/**
 * A movement's source as it was stored, in the app's language where the app wrote it — a stock typed over, an
 * inventory, a perte type; a client's or supplier's name as written.
 */
@Composable
fun movementSourceLabel(type: String, label: String): String {
    INVENTORY_LABEL.matchEntire(label)?.let { return stringResource(R.string.mouvements_source_inventory, it.groupValues[1]) }
    return when {
        label == "Stock initial"     -> stringResource(R.string.mouvements_source_initial_stock)
        label == "Ajustement manuel" -> stringResource(R.string.mouvements_source_manual_adjustment)
        type == "perte"              -> BuiltInNames.perteType(label)
        else                         -> label
    }
}

private val INVENTORY_LABEL = Regex("""Inventaire session #(\d+)""")

@Composable
fun formatMovementDateLabel(date: String): String {
    val today     = java.time.LocalDate.now().toString()
    val yesterday = java.time.LocalDate.now().minusDays(1).toString()
    return when (date) {
        today     -> stringResource(R.string.common_period_today)
        yesterday -> stringResource(R.string.common_period_yesterday)
        else      -> date
    }
}


// ── Sub-screen 5 : Détail du mouvement ──
@Composable
fun MovementDetailView(
    movementId : Int,
    viewModel  : StockMovementViewModel,
    onBack     : () -> Unit,
    unit       : String? = null,
) {
    val money = LocalMoneyFormatter.current
    LaunchedEffect(movementId) { viewModel.loadMovementDetail(movementId) }
    val movement by viewModel.selectedMovement.collectAsState()
    val sourceNumber by viewModel.selectedSourceNumber.collectAsState()
    val document by viewModel.selectedDocument.collectAsState()
    val drill = LocalDrillDown.current
    val currentMovement = movement

    if (currentMovement == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DsColors.Primary)
        }
        return
    }
    val isEntree = currentMovement.direction == "entree"
    val typeLabel = movementTypeLabel(currentMovement.type)

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title   = stringResource(R.string.mouvements_detail_title),
            leading = DsTopBarLeading.Back(onBack)
        )

        // Scrolls: in landscape, or at a large font, the details and the document button run past the screen
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.md, bottom = DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Type + badge ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(typeLabel, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Box(
                    modifier = Modifier
                        .clip(DsShapes.pill)
                        .background(if (isEntree) DsColors.SuccessLight else DsColors.DangerLight)
                        .padding(horizontal = DsSpacing.md, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(if (isEntree) R.string.mouvements_in_one else R.string.mouvements_out_one),
                        color      = if (isEntree) DsColors.Success else DsColors.Danger,
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(formatMovementDateTime(currentMovement.created_at), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)

            // ── Quantité ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .padding(DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.common_label_quantity), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                Text(
                    "${if (isEntree) "+" else "-"}${formatQty(currentMovement.quantity)}",
                    fontSize   = DsTextSize.display,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isEntree) DsColors.Success else DsColors.Danger
                )
                Text(unit?.let { unitWordText(currentMovement.quantity, it).asString() } ?: stringResource(R.string.mouvements_units), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }

            // ── Infos ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.Surface)
                    .border(1.dp, DsColors.Border, DsShapes.large)
                    .padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                DetailInfoRow(stringResource(R.string.products_label_product), currentMovement.product_name)
                DetailInfoRow(stringResource(R.string.mouvements_source), movementSourceLabel(currentMovement.type, currentMovement.source_label))
                DetailInfoRow(stringResource(R.string.mouvements_source_number), sourceNumber ?: "#${currentMovement.source_id}")
                currentMovement.unit_price?.let {
                    DetailInfoRow(stringResource(R.string.mouvements_unit_price), money.da(it))
                }
                DetailInfoRow(stringResource(R.string.pertes_stat_value), money.da(currentMovement.total_value))
                DetailInfoRow(
                    stringResource(R.string.mouvements_notes),
                    // The one note the app writes itself (an inventory zeroing what was not counted), in the app's language
                    when (val note = currentMovement.note) {
                        null                         -> "—"
                        "Non inventorié, mis à zéro" -> stringResource(R.string.mouvements_note_not_counted)
                        else                         -> note
                    }
                )
            }

            // The record behind the movement: why the stock moved, one tap away.
            document?.let { (kind, id) ->
                val (label, target) = when (kind) {
                    "vente"               -> stringResource(R.string.mouvements_open_sale) to DrillTarget.Vente(id)
                    "bon"                 -> stringResource(R.string.mouvements_open_bon) to DrillTarget.Bon(id)
                    "perte"               -> stringResource(R.string.mouvements_open_perte) to DrillTarget.Perte(id)
                    "inventaire"          -> stringResource(R.string.mouvements_open_inventory) to DrillTarget.Inventaire(id)
                    "retours_client"      -> stringResource(R.string.mouvements_open_client_returns) to DrillTarget.RetoursClient(id)
                    else                  -> stringResource(R.string.mouvements_open_supplier_returns) to DrillTarget.RetoursFournisseur(id)
                }
                OutlinedButton(
                    onClick  = { drill(target) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

fun formatMovementDateTime(iso: String): String = runCatching {
    val instant = java.time.Instant.parse(iso)
    val local   = instant.atZone(java.time.ZoneId.systemDefault())
    com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.pertes_day_at_time, local.toLocalDate().toString(), "%02d".format(java.util.Locale.ROOT, local.hour) + ":" + "%02d".format(java.util.Locale.ROOT, local.minute)))
}.getOrDefault(iso)