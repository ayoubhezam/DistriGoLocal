package com.distrigo.app.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.ui.components.ReceiptPreviewSheet
import com.distrigo.app.ui.components.ShareOptionsSheet
import com.distrigo.app.ui.components.toReceiptData
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
@Composable
fun PurchaseOrderDetailScreen(
    order      : PurchaseOrder,
    onBack     : () -> Unit,
    onReceived : () -> Unit,
    viewModel  : PurchaseViewModel,
    productViewModel: ProductViewModel = viewModel(),
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val displayOrder  = selectedOrder ?: order
    val isReceived    = displayOrder.status == "received"
    var isLoading     by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }
    var receiveUserName   by remember { mutableStateOf("") }
    var showReopenDialog  by remember { mutableStateOf(false) }
    var showEditScreen    by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showReceiptPreview by remember { mutableStateOf(false) }
    var showShareOptions    by remember { mutableStateOf(false) }
    var deleteError       by remember { mutableStateOf("") }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    if (showReopenDialog) {
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text("Rouvrir le bon ?") },
            text  = {
                Text(
                    "Ce bon a déjà été reçu. Le rouvrir annulera la mise à jour du stock. Voulez-vous continuer ?",
                    fontSize = DsTextSize.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReopenDialog = false
                    isLoading = true
                    viewModel.reopenOrder(
                        id        = displayOrder.id,
                        onSuccess = {
                            isLoading     = false
                            showEditScreen = true
                        },
                        onError   = { isLoading = false }
                    )
                }) {
                    Text("Rouvrir", color = DsColors.Warning, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showEditScreen) {
        BackHandler {
            showEditScreen = false
            onFullScreenChange(false)
        }
        onFullScreenChange(true)
        PurchaseFormScreen(
            order             = displayOrder,
            onBack            = {
                showEditScreen = false
                onFullScreenChange(false)
            },
            onSaved           = {
                showEditScreen = false
                onFullScreenChange(false)
                viewModel.loadOrderDetail(displayOrder.id)
                viewModel.loadOrders()
            },
            purchaseViewModel = viewModel
        )
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteError = "" },
            title = { Text("Supprimer le bon ?") },
            text  = {
                Column {
                    Text("Voulez-vous supprimer le bon #${displayOrder.id} ?")
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOrder(
                        id        = displayOrder.id,
                        onSuccess = {
                            showDeleteDialog = false
                            onBack()
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
                TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showReceiptPreview) {
        ReceiptPreviewSheet(
            receipt          = displayOrder.toReceiptData(context),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions    = true
            }
        )
    }

    if (showShareOptions) {
        ShareOptionsSheet(
            receipt   = displayOrder.toReceiptData(context),
            onDismiss = { showShareOptions = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.SurfaceSunken)
    ) {
        // ── Header (Outside Ticket) ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment     = Alignment.CenterVertically
        )
        {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Achat #${displayOrder.id}", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(displayOrder.supplier_name, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (isReceived) DsColors.SurfaceSunken else DsColors.WarningLight)
                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
            ) {
                Text(
                    if (isReceived) "Reçu" else "En attente",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isReceived) DsColors.TextSecondary else DsColors.Warning
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))

            Box {
                IconButton(onClick = { overflowMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = overflowMenuExpanded,
                    onDismissRequest = { overflowMenuExpanded = false }
                ) {
                    if (isReceived) {
                        DropdownMenuItem(
                            text = { Text("Rouvrir le bon", color = DsColors.Warning) },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = DsColors.Warning) },
                            onClick = {
                                overflowMenuExpanded = false
                                showReopenDialog = true
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Modifier", color = DsColors.Primary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary) },
                            onClick = {
                                overflowMenuExpanded = false
                                showEditScreen = true
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = DsColors.Danger) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger) },
                        onClick = {
                            overflowMenuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            modifier            = Modifier.weight(1f)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = DsSpacing.xxl),
                    shape = com.distrigo.app.ui.common.TicketShape(),
                    colors = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Supplier Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(DsShapes.pill)
                                    .background(if (isReceived) DsColors.SurfaceSunken else DsColors.PrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Business, contentDescription = null, tint = if (isReceived) DsColors.TextTertiary else DsColors.Primary)
                            }
                            Spacer(Modifier.width(DsSpacing.md))
                            Column {
                                Text(displayOrder.supplier_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                Text(displayOrder.date, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                        Spacer(Modifier.height(DsSpacing.md))

                        Text(
                            "ARTICLES (${displayOrder.items?.size ?: 0})",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(DsSpacing.sm))

                        displayOrder.items?.let { itemsList ->
                            itemsList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = DsSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(DsShapes.small)
                                            .background(if (isReceived) DsColors.SurfaceSunken else DsColors.PrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = if (isReceived) DsColors.TextTertiary else DsColors.Primary, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(DsSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                                        Text(
                                            "${formatQty(item.quantity)} ${item.unit_type} × ${"%.2f".format(item.unit_cost)} DA",
                                            fontSize = DsTextSize.caption,
                                            color    = DsColors.TextSecondary
                                        )
                                    }
                                    Text(
                                        "${"%.2f".format(item.total_cost)} DA",
                                        fontSize   = DsTextSize.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color      = DsColors.TextPrimary
                                    )
                                }
                                if (index < itemsList.size - 1) {
                                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                                }
                            }
                        }

                        Spacer(Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = DsColors.Border, thickness = 2.dp)
                        Spacer(Modifier.height(DsSpacing.md))

                        // Total Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = if (isReceived) DsColors.TextPrimary else DsColors.Primary, letterSpacing = 1.sp)
                            Text("${"%.2f".format(displayOrder.total)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = if (isReceived) DsColors.TextPrimary else DsColors.Primary)
                        }

                        Spacer(Modifier.height(DsSpacing.md))

                        // Payment Rows
                        val montantPaye = displayOrder.montant_paye ?: 0.0
                        val reste       = displayOrder.total - montantPaye

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Montant payé", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Success)
                            Text("${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Success)
                        }

                        Spacer(Modifier.height(DsSpacing.sm))

                        if (reste > 0) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Reste", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                                Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                            }
                        } else {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                com.distrigo.app.ui.common.PaidStamp()
                            }
                        }

                        displayOrder.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(Modifier.height(DsSpacing.md))
                            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                            Spacer(Modifier.height(DsSpacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Text(note, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom Area ──
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

        if (!isReceived) {
            Button(
                onClick  = { showReceiveDialog = true },
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                    .height(52.dp),
                shape    = DsShapes.large,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Marquer comme reçu", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Achat finalisé et enregistré", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            }
        }

        if (showReceiveDialog) {
            AlertDialog(
                onDismissRequest = { showReceiveDialog = false },
                title = { Text("Confirmer la réception", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Le stock du dépôt sera mis à jour pour tous les articles de ce bon.",
                            fontSize = DsTextSize.bodySmall,
                            color = DsColors.TextSecondary
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        OutlinedTextField(
                            value         = receiveUserName,
                            onValueChange = { receiveUserName = it },
                            placeholder   = { Text("Effectué par (optionnel)") },
                            leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReceiveDialog = false
                            isLoading = true
                            viewModel.receiveOrder(
                                id        = displayOrder.id,
                                userName  = receiveUserName.trim().ifEmpty { null },
                                onSuccess = {
                                    onReceived()
                                },
                                onError   = { isLoading = false }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
                    ) { Text("Confirmer") }
                },
                dismissButton = {
                    TextButton(onClick = { showReceiveDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}
