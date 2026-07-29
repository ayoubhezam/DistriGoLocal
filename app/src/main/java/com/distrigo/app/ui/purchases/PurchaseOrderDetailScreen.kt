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

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        productViewModel.loadProducts()
    }

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
                productViewModel.loadProducts()
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
                            productViewModel.loadProducts()
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
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        )
        {
            // زر الحذف
            IconButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(DsShapes.medium)
                    .background(DsColors.DangerLight)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint     = DsColors.Danger,
                    modifier = Modifier.size(18.dp)
                )
            }

            // زر التعديل
            IconButton(
                onClick  = {
                    if (displayOrder.status == "received") {
                        showReopenDialog = true
                    } else {
                        showEditScreen = true
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(DsShapes.medium)
                    .background(DsColors.PrimaryLight)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Modifier",
                    tint     = DsColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Status badge
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (isReceived) DsColors.SuccessLight else DsColors.WarningLight)
                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
            ) {
                Text(
                    if (isReceived) "Reçu" else "En attente",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isReceived) DsColors.Success else DsColors.Warning
                )
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.weight(1f)
        ) {
            // ── Supplier Card ──
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = DsShapes.large,
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Row(
                        modifier          = Modifier.padding(DsSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(42.dp)
                                .clip(DsShapes.medium)
                                .background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(displayOrder.supplier_name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                            Text(displayOrder.date, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        }
                    }
                }
            }

            // ── Items ──
            item {
                Text("Articles", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            }

            displayOrder.items?.let { itemsList ->
                items(itemsList) { item ->
                    val products by productViewModel.products.collectAsState()
                    val matchedProduct = remember(products, item.product_id) { products.find { it.id == item.product_id } }

                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = DsShapes.large,
                        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Row(
                            modifier              = Modifier.padding(DsSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier              = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier         = Modifier
                                        .size(36.dp)
                                        .clip(DsShapes.medium)
                                        .background(DsColors.PrimaryLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bitmap = remember(matchedProduct?.image_uri) {
                                        matchedProduct?.image_uri?.let { uri ->
                                            val imageBytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.NO_WRAP)
                                            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        }
                                    }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(), contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Column {
                                    Text(item.product_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                    Text("${formatQty(item.quantity)} ${item.unit_type} × ${"%.2f".format(item.unit_cost)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                            }
                            Text("${"%.2f".format(item.total_cost)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                        }
                    }
                }
            }


// ── Total ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.PrimaryLight)
                        .padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Total", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                        Text("${"%.2f".format(displayOrder.total)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
                    }

                    val montantPaye = displayOrder.montant_paye ?: 0.0
                    val reste       = displayOrder.total - montantPaye

                    HorizontalDivider(color = DsColors.Primary.copy(alpha = 0.2f), thickness = 0.5.dp)

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé", fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.Medium)
                        Text("${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Success)
                    }

                    if (reste > 0) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Reste (Impayé)", fontSize = DsTextSize.bodySmall, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                            Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                        }
                    } else {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.Medium)
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = DsTextSize.bodySmall, color = DsColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Note ──
            displayOrder.note?.let { note ->
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = DsShapes.large,
                        colors    = CardDefaults.cardColors(containerColor = DsColors.SurfaceSunken),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(modifier = Modifier.padding(DsSpacing.md), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Text(note, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                        }
                    }
                }
            }

            // ── Actions: Aperçu & Imprimer / Partager ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = { showReceiptPreview = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = DsShapes.large,
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aperçu & Imprimer", fontSize = DsTextSize.caption, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick  = { showShareOptions = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = DsShapes.large,
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Partager", fontSize = DsTextSize.caption, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Receive Button ──
            if (!isReceived) {
                item {
                    Button(
                        onClick  = { showReceiveDialog = true },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                }
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
                                    productViewModel.loadProducts()
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
