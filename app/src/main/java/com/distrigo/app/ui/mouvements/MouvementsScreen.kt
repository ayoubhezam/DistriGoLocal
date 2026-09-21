package com.distrigo.app.ui.mouvements

import com.distrigo.app.data.time.BusinessDates
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

private fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", v)


@Composable
fun MouvementsScreen(
    product         : Product,
    onBack          : () -> Unit,
    onMovementClick : (StockMovement) -> Unit,
    viewModel       : StockMovementViewModel = hiltViewModel()
) {

    val filters by viewModel.filters.collectAsState()
    val movements by viewModel.movements.collectAsState()
    val clients   by viewModel.clients.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }

    LaunchedEffect(product.id) { viewModel.loadPartiesForProduct(product.id) }
    LaunchedEffect(product.id, filters) { viewModel.loadFilteredMovements(product.id, filters) }

    if (filtersOpen) {
        MovementFiltersSheet(
            filters   = filters,
            clients   = clients,
            suppliers = suppliers,
            countOf   = { draft -> viewModel.countFor(product.id, draft) },
            onApply   = { viewModel.setFilters(it); filtersOpen = false },
            onDismiss = { filtersOpen = false }
        )
    }

    // ── Detail Sub-screen ──

    // ── Main List ──
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title   = "Mouvements",
            leading = DsTopBarLeading.Back(onBack)
        ) {
            Box {
                IconButton(onClick = { filtersOpen = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filtres",
                        tint = if (filters.activeCount > 0) DsColors.Primary else DsColors.TextSecondary
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
                Text("Stock actuel : ${formatQty(product.stock)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Stats ──
        val entrees = movements.filter { it.direction == "entree" }.sumOf { it.quantity }
        val sorties = movements.filter { it.direction == "sortie" }.sumOf { it.quantity }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = "Entrées",    value = formatQty(entrees),  color = DsColors.Success)
            StatCard(modifier = Modifier.weight(1f), label = "Sorties",    value = formatQty(sorties),  color = DsColors.Danger)
            StatCard(modifier = Modifier.weight(1f), label = "Mouvements", value = "${movements.size}", color = DsColors.Primary)
        }

        Spacer(Modifier.height(DsSpacing.md))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            movements.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(DsSpacing.md))
                        Text("Aucun mouvement", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> {
                val grouped = movements.groupBy { BusinessDates.localDay(it.created_at) }
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    grouped.forEach { (date, dayMovements) ->
                        item(key = "header_$date") {
                            Text(
                                formatMovementDateLabel(date),
                                fontSize   = DsTextSize.caption,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(top = DsSpacing.sm, bottom = DsSpacing.xs)
                            )
                        }
                        items(dayMovements, key = { it.id }) { movement ->
                            MovementRow(movement = movement, onClick = { onMovementClick(movement) })
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
            .background(DsColors.SurfaceMuted)
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
    val (icon, typeLabel) = movementTypeDisplay(movement.type)

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
            Text(movement.source_label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
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
fun movementTypeDisplay(type: String): Pair<ImageVector, String> = when (type) {
    "achat"      -> Icons.Default.Inventory to "Achat"
    "vente"      -> Icons.Default.PointOfSale to "Vente"
    "chargement" -> Icons.Default.LocalShipping to "Chargement"
    "perte"      -> Icons.Default.Warning to "Perte"
    "ajustement" -> Icons.Default.Tune to "Ajustement"
    "retour_client"      -> Icons.Default.AssignmentReturn to "Retour client"
    "retour_fournisseur" -> Icons.Default.AssignmentReturn to "Retour fournisseur"
    else         -> Icons.Default.SwapVert to type
}

fun formatMovementDateLabel(date: String): String {
    val today     = java.time.LocalDate.now().toString()
    val yesterday = java.time.LocalDate.now().minusDays(1).toString()
    return when (date) {
        today     -> "Aujourd'hui"
        yesterday -> "Hier"
        else      -> date
    }
}


// ── Sub-screen 5 : Détail du mouvement ──
@Composable
fun MovementDetailView(
    movementId : Int,
    viewModel  : StockMovementViewModel,
    onBack     : () -> Unit
) {
    LaunchedEffect(movementId) { viewModel.loadMovementDetail(movementId) }
    val movement by viewModel.selectedMovement.collectAsState()
    val sourceNumber by viewModel.selectedSourceNumber.collectAsState()
    val currentMovement = movement

    if (currentMovement == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DsColors.Primary)
        }
        return
    }
    val isEntree = currentMovement.direction == "entree"
    val (_, typeLabel) = movementTypeDisplay(currentMovement.type)

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title   = "Détail du mouvement",
            leading = DsTopBarLeading.Back(onBack)
        )

        Spacer(Modifier.height(DsSpacing.md))

        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
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
                        if (isEntree) "Entrée" else "Sortie",
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
                    .background(DsColors.SurfaceMuted)
                    .padding(DsSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Quantité", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                Text(
                    "${if (isEntree) "+" else "-"}${formatQty(currentMovement.quantity)}",
                    fontSize   = DsTextSize.display,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isEntree) DsColors.Success else DsColors.Danger
                )
                Text("Pièces", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
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
                DetailInfoRow("Produit", currentMovement.product_name)
                DetailInfoRow("Source", currentMovement.source_label)
                DetailInfoRow("N° de source", sourceNumber ?: "#${currentMovement.source_id}")
                currentMovement.unit_price?.let {
                    DetailInfoRow("Prix unitaire", "${"%.2f".format(it)} DA")
                }
                DetailInfoRow("Valeur totale", "${"%.2f".format(currentMovement.total_value)} DA")
                DetailInfoRow("Utilisateur", currentMovement.user_name ?: "—")
                DetailInfoRow("Notes", currentMovement.note ?: "—")
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
    "${local.toLocalDate()} à ${"%02d".format(local.hour)}:${"%02d".format(local.minute)}"
}.getOrDefault(iso)