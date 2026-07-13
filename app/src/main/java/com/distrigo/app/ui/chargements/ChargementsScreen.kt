package com.distrigo.app.ui.chargements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Chargement
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import java.time.LocalDate

@Composable
fun ChargementsScreen(
    viewModel: ChargementViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val chargements by viewModel.chargements.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val error        by viewModel.error.collectAsState()
    val productViewModel: ProductViewModel = viewModel()

    var showNewChargement    by remember { mutableStateOf(false) }
    var selectedChargement   by remember { mutableStateOf<Chargement?>(null) }
    var longPressChargement  by remember { mutableStateOf<Chargement?>(null) }
    var showDeleteDialog     by remember { mutableStateOf(false) }
    var deleteError          by remember { mutableStateOf("") }

    // ── New Chargement Screen ──
    if (showNewChargement) {
        ChargementFormScreen(
            onBack  = { showNewChargement = false },
            onSaved = {
                showNewChargement = false
                viewModel.loadChargements()
            }
        )
        return
    }

    // ── Delete Confirmation Dialog ──
    if (showDeleteDialog) {
        val target = selectedChargement ?: longPressChargement
        if (target != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; deleteError = "" },
                title = { Text("Supprimer le mouvement ?") },
                text  = {
                    Column {
                        Text("Voulez-vous supprimer ce mouvement #${target.id} ? Les stocks seront rétablis.")
                        if (deleteError.isNotEmpty()) {
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteChargement(
                            id        = target.id,
                            onSuccess = {
                                showDeleteDialog    = false
                                longPressChargement  = null
                                selectedChargement   = null
                                deleteError          = ""
                                productViewModel.loadProducts()
                            },
                            onError = { error -> deleteError = error }
                        )
                    }) {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }

    // ── Long Press Dialog ──
    longPressChargement?.let { chargement ->
        if (!showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { longPressChargement = null },
                title = { Text("Mouvement #${chargement.id}") },
                confirmButton = {},
                dismissButton = {},
                shape = DsShapes.medium,
                containerColor = DsColors.Surface,
                text = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .clickable { showDeleteDialog = true }
                            .padding(DsSpacing.md),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                        Text("Supprimer", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    }

    // ── Detail Screen ──
    selectedChargement?.let { chargement ->
        BackHandler { selectedChargement = null }

        val versCamionCount = chargement.items?.count { it.direction == "vers_camion" } ?: 0
        val versDepotCount  = chargement.items?.count { it.direction == "vers_depot" } ?: 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedChargement = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Text(
                        "Chargement #${chargement.id}",
                        fontSize   = DsTextSize.title,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.TextPrimary
                    )
                }
                IconButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(DsShapes.small)
                        .background(DsColors.DangerLight)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint     = DsColors.Danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                modifier            = Modifier.weight(1f)
            ) {
                // ── Date/time info card ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.SurfaceMuted)
                            .padding(DsSpacing.lg),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        Box(
                            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                formatOrderDate(chargement.created_at?.take(10) ?: ""),
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextPrimary
                            )
                            Text(
                                formatOrderTime(chargement.created_at),
                                fontSize = DsTextSize.caption,
                                color    = DsColors.TextSecondary
                            )
                        }
                    }
                }

                item {
                    Text(
                        "Articles",
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary
                    )
                }

                chargement.items?.let { itemsList ->
                    items(itemsList, key = { it.id }) { item ->
                        val toCamion = item.direction == "vers_camion"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.Surface)
                                .border(1.dp, DsColors.Border, DsShapes.large)
                                .padding(DsSpacing.md),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.product_name,
                                    fontSize   = DsTextSize.body,
                                    fontWeight = FontWeight.Medium,
                                    color      = DsColors.TextPrimary,
                                    maxLines   = 1
                                )
                                Text(
                                    "${item.quantity} ${item.unit_type}",
                                    fontSize = DsTextSize.caption,
                                    color    = DsColors.TextSecondary
                                )
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(if (toCamion) DsColors.PrimaryLight else DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
                            ) {
                                Text(
                                    if (toCamion) "Vers camion" else "Vers dépôt",
                                    fontSize   = DsTextSize.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (toCamion) DsColors.Primary else DsColors.Success
                                )
                            }
                        }
                    }
                }

                chargement.note?.let { note ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.SurfaceMuted)
                                .padding(DsSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Text(note, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                        }
                    }
                }
            }
        }
        return
    }

    // ── Main List Screen ──
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Chargements",
                fontSize   = DsTextSize.headline,
                fontWeight = FontWeight.ExtraBold,
                color      = DsColors.TextPrimary
            )
            IconButton(
                onClick  = { showNewChargement = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(DsShapes.pill)
                    .background(DsColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau mouvement", tint = Color.White)
            }
        }

        // ── Stats ──
        val weekAgo = LocalDate.now().minusDays(7)
        val weekCount = chargements.count { chargement ->
            try {
                LocalDate.parse(chargement.created_at?.take(10)).isAfter(weekAgo)
            } catch (e: Exception) { false }
        }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            ChargementStatBox(modifier = Modifier.weight(1f), label = "Total mouvements", value = chargements.size.toString())
            ChargementStatBox(modifier = Modifier.weight(1f), label = "Cette semaine",     value = weekCount.toString())
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Loading ──
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return
        }

        // ── Error ──
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = DsColors.Danger)
            }
            return
        }

        // ── Empty State ──
        if (chargements.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint     = DsColors.TextTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(DsSpacing.md))
                    Text("Aucun mouvement enregistré", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            return
        }

        val groupedChargements = chargements.groupBy { it.created_at?.take(10) ?: "" }

        // ── Timeline List ──
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            modifier            = Modifier.weight(1f)
        ) {
            groupedChargements.forEach { (date, dayChargements) ->
                item {
                    Text(
                        text       = formatOrderDate(date),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary,
                        modifier   = Modifier.padding(vertical = DsSpacing.sm)
                    )
                }
                items(dayChargements, key = { it.id }) { chargement ->
                    ChargementCard(
                        chargement  = chargement,
                        onClick     = {
                            selectedChargement = chargement
                            viewModel.loadChargementDetail(chargement.id)
                        },
                        onLongClick = { longPressChargement = chargement }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargementStatBox(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChargementCard(chargement: Chargement, onClick: () -> Unit, onLongClick: () -> Unit) {
    val versCamionCount = chargement.items?.count { it.direction == "vers_camion" } ?: 0
    val versDepotCount  = chargement.items?.count { it.direction == "vers_depot" } ?: 0

    val (iconBg, icon, iconTint) = when {
        versCamionCount > 0 && versDepotCount == 0 -> Triple(DsColors.PrimaryLight, Icons.Default.LocalShipping, DsColors.Primary)
        versDepotCount > 0 && versCamionCount == 0 -> Triple(DsColors.SuccessLight, Icons.Default.Warehouse, DsColors.Success)
        else -> Triple(DsColors.SurfaceSunken, Icons.Default.SwapVert, DsColors.TextSecondary)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick
            )
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(DsSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Chargement #${chargement.id}",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextSecondary
                )
                Text(
                    formatOrderTime(chargement.created_at),
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary
                )
            }
            Text(
                text = when {
                    versDepotCount == 0  -> "Vers camion · $versCamionCount article(s)"
                    versCamionCount == 0 -> "Vers dépôt · $versDepotCount article(s)"
                    else                 -> "$versCamionCount vers camion · $versDepotCount vers dépôt"
                },
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.TextPrimary
            )
            chargement.note?.let { note ->
                Text(note, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
            }
        }

        Spacer(Modifier.width(DsSpacing.sm))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
    }
}