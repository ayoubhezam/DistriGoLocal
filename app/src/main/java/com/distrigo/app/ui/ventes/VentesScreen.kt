package com.distrigo.app.ui.ventes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.formatQty
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import com.distrigo.app.ui.purchases.UnifiedStatColumn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.clients.ClientViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import com.distrigo.app.ui.components.ReceiptPreviewSheet
import com.distrigo.app.ui.components.ShareOptionsSheet
import com.distrigo.app.ui.components.toReceiptData

@Composable
fun VentesScreen(
    viewModel         : VenteViewModel   = viewModel(),
    modifier          : Modifier         = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {},
    productViewModel  : ProductViewModel = viewModel(),
    clientViewModel   : ClientViewModel  = viewModel()
){
    val ventes    by viewModel.ventes.collectAsState()
    val depotVentes = ventes.filter { it.source == "depot" }
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    var showNewVente     by remember { mutableStateOf(false) }
    var selectedVente    by remember { mutableStateOf<Vente?>(null) }
    var longPressVente   by remember { mutableStateOf<Vente?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingVenteId   by remember { mutableStateOf<Int?>(null) }
    var deleteError      by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadVentes() }
    // ── New Vente Screen ──
    if (showNewVente) {
        onFullScreenChange(true)
        BackHandler {
            showNewVente = false
            onFullScreenChange(false)
        }
        VenteFormScreen(
            onBack  = {
                showNewVente = false
                onFullScreenChange(false)
            },
            onSaved = {
                showNewVente = false
                onFullScreenChange(false)
                viewModel.loadVentes()
            }
        )
        return
    }

    // ── Edit Vente Screen ──
    editingVenteId?.let { id ->
        val fullVenteState by viewModel.selectedVente.collectAsState()
        val fullVente = fullVenteState
        if (fullVente != null && fullVente.id == id) {
            onFullScreenChange(true)
            BackHandler {
                editingVenteId = null
                onFullScreenChange(false)
            }
            // NOTE: VenteFormScreen doesn't support an edit mode yet — wired up as
            // navigation structure only; will show the "new vente" form for now.
            VenteFormScreen(
                onBack  = {
                    editingVenteId = null
                    onFullScreenChange(false)
                },
                onSaved = {
                    editingVenteId = null
                    onFullScreenChange(false)
                    viewModel.loadVentes()
                }
            )
            return
        }
    }

    // ── Detail Screen ──
    selectedVente?.let { vente ->
        VenteDetailScreen(
            vente             = vente,
            onBack            = { selectedVente = null },
            viewModel         = viewModel,
            productViewModel  = productViewModel,
            clientViewModel   = clientViewModel,
            onDelivered       = { selectedVente = null; viewModel.loadVentes() },
            onDeleted         = { selectedVente = null; viewModel.loadVentes() }
        )
        return
    }

    // ── Long Press Dialog ──
    longPressVente?.let { vente ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; longPressVente = null; deleteError = "" },
                title = { Text("Supprimer la vente ?") },
                text  = {
                    Column {
                        Text("Voulez-vous supprimer la vente #${vente.id} de ${vente.client_name} ?")
                        if (deleteError.isNotEmpty()) {
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteVente(
                            id        = vente.id,
                            onSuccess = {
                                showDeleteDialog = false
                                longPressVente   = null
                                deleteError      = ""
                                viewModel.loadVentes()
                                productViewModel.loadProducts()
                                clientViewModel.loadClients()
                            },
                            onError = { error ->
                                deleteError = error
                            }
                        )
                    }) {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; longPressVente = null; deleteError = "" }) {
                        Text("Annuler")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { longPressVente = null },
                title = { Text("Vente #${vente.id}") },
                confirmButton = {},
                dismissButton = {},
                icon = null,
                properties = androidx.compose.ui.window.DialogProperties(),
                shape = DsShapes.large,
                containerColor = DsColors.Surface,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.PrimaryLight)
                                .clickable {
                                    longPressVente = null
                                    viewModel.loadVenteDetail(vente.id)
                                    editingVenteId = vente.id
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                            Text("Modifier la vente", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.DangerLight)
                                .clickable { showDeleteDialog = true }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                            Text("Supprimer la vente", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ventes", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            // ── Stats ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg)
                    .clip(DsShapes.large)
                    .background(DsColors.Primary)
                    .padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                UnifiedStatColumn(icon = Icons.Default.Receipt,     value = depotVentes.size.toString(), label = "Total bons")
                UnifiedStatColumn(icon = Icons.Default.Schedule,    value = depotVentes.count { it.status == "pending"   }.toString(), label = "En attente")
                UnifiedStatColumn(icon = Icons.Default.CheckCircle, value = depotVentes.count { it.status == "delivered" }.toString(), label = "Reçus")
            }

            Spacer(Modifier.height(DsSpacing.md))

            Text(
                "Historique des ventes",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )

            Spacer(Modifier.height(DsSpacing.sm))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = DsColors.Danger)
                }
            } else if (depotVentes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null,
                            tint = DsColors.TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(DsSpacing.md))
                        Text("Aucune vente", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Appuyez sur + pour créer", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
            } else {
                val groupedVentes =
                    depotVentes.groupBy { vente -> vente.created_at?.take(10) ?: "" }

                // ── List ──
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xs),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedVentes.forEach { (date, dayVentes) ->
                        // ── Date Header ──
                        item {
                            Text(
                                text       = formatOrderDate(date),
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(vertical = DsSpacing.sm)
                            )
                        }
                        items(dayVentes) { vente ->
                            VenteCard(
                                vente       = vente,
                                onClick     = {
                                    selectedVente = vente
                                    viewModel.loadVenteDetail(vente.id)
                                },
                                onLongClick = {
                                    longPressVente = vente
                                }
                            )
                        }
                    }
                }
            }
        }

        com.distrigo.app.ui.components.ScrollAwareFab(
            onClick  = { showNewVente = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(DsSpacing.lg)
        )
    }
}

@Composable
fun VenteDetailScreen(
    vente             : Vente,
    onBack            : () -> Unit,
    viewModel         : VenteViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    productViewModel  : com.distrigo.app.ui.products.ProductViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    clientViewModel   : com.distrigo.app.ui.clients.ClientViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    onDelivered       : () -> Unit = {},
    onDeleted         : () -> Unit = {}
) {
    BackHandler { onBack() }


    val fullVenteState by viewModel.selectedVente.collectAsState()
    val vente = fullVenteState?.takeIf { it.id == vente.id } ?: vente
    val isDelivered = vente.status == "delivered"
    var isDelivering by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showReceiptPreview by remember { mutableStateOf(false) }
    var showShareOptions    by remember { mutableStateOf(false) }

    if (showReceiptPreview) {
        ReceiptPreviewSheet(
            receipt          = vente.toReceiptData(context),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions    = true
            }
        )
    }

    if (showShareOptions) {
        ShareOptionsSheet(
            receipt   = vente.toReceiptData(context),
            onDismiss = { showShareOptions = false }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteError = "" },
            title = { Text("Supprimer ce reçu ?") },
            text  = {
                Column {
                    Text("Cette action est irréversible. Les quantités vendues seront remises en stock.")
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        viewModel.deleteVente(
                            id        = vente.id,
                            onSuccess = {
                                isDeleting = false
                                showDeleteDialog = false
                                viewModel.loadVentes()
                                productViewModel.loadProducts()
                                clientViewModel.loadClients()
                                onDeleted()
                            },
                            onError = { error -> isDeleting = false; deleteError = error }
                        )
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = DsColors.Danger, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Vente #${vente.id}", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(vente.client_name, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (isDelivered) DsColors.SuccessLight else DsColors.WarningLight)
                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
            ) {
                Text(
                    if (isDelivered) "Livré" else "En attente",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isDelivered) DsColors.Success else DsColors.Warning
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))
            IconButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.DangerLight)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            item {
                Text(
                    "Articles (${vente.items?.size ?: vente.items_count ?: 0})",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextSecondary,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }

            vente.items?.let { itemsList ->
                items(itemsList, key = { it.id }) { item ->
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = DsShapes.large,
                        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Row(modifier = Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                                Text(
                                    "${formatQty(item.quantity)} ${item.unit_type} × ${"%.2f".format(item.unit_price)} DA",
                                    fontSize = DsTextSize.caption,
                                    color    = DsColors.TextSecondary
                                )
                            }
                            Text(
                                "${"%.2f".format(item.total_price)} DA",
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color      = DsColors.Primary
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Total", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                    Text("${"%.2f".format(vente.total)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
                }
            }

            item {
                val montantPaye = vente.montant_paye ?: 0.0
                val reste       = vente.total - montantPaye
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.SurfaceSunken)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Montant payé", fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                        Text("${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    }
                    if (reste > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reste", fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                            Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                        }
                    }
                }
            }

            vente.note?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.SurfaceSunken)
                            .padding(DsSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                        Text(note, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick  = { showReceiptPreview = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = DsShapes.medium,
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aperçu & Imprimer", fontSize = DsTextSize.caption, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick  = { showShareOptions = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = DsShapes.medium,
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Partager", fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!isDelivered) {
            Button(
                onClick = {
                    isDelivering = true
                    viewModel.deliverVente(
                        id        = vente.id,
                        onSuccess = {
                            isDelivering  = false
                            viewModel.loadVentes()
                            productViewModel.loadProducts()
                            clientViewModel.loadClients()
                            onDelivered()
                        },
                        onError = { isDelivering = false }
                    )
                },
                enabled  = !isDelivering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                    .height(52.dp),
                shape  = DsShapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
            ) {
                if (isDelivering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Marquer comme livré", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VenteCard(vente: Vente, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isDelivered = vente.status == "delivered"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { onLongClick() }
            ),
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(DsShapes.medium)
                    .background(if (isDelivered) DsColors.SuccessLight else DsColors.WarningLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PointOfSale,
                    contentDescription = null,
                    tint     = if (isDelivered) DsColors.Success else DsColors.Warning,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Vente #${vente.id}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                    Text(
                        formatOrderTime(vente.created_at),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        vente.client_name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = DsTextSize.bodyLarge,
                        color      = DsColors.TextPrimary,
                        maxLines   = 1,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(DsSpacing.sm))
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(if (isDelivered) DsColors.SuccessLight else DsColors.WarningLight)
                            .padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                    ) {
                        Text(
                            if (isDelivered) "Livré" else "En attente",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isDelivered) DsColors.Success else DsColors.Warning
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%.2f".format(vente.total)} DA",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary
                    )
                    Text(
                        "· ${vente.items_count ?: vente.items?.size ?: 0} article(s)",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
        }
    }
}
